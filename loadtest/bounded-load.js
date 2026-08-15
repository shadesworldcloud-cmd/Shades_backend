#!/usr/bin/env node
/**
 * Bounded, mixed read/write load test.
 *
 * Plain Node with no dependencies on purpose — adding k6 or JMeter to run one focused test would be
 * more infrastructure than the question needs, and this has to be runnable by anyone who can
 * already build the project.
 *
 * What it actually measures, stated plainly because the previous wording here claimed more than the
 * code did: catalogue reads, the best-sellers aggregate, a product detail, a cart read, and the
 * automatic-offer quote. Every one is a read. There is no authenticated cart write and no checkout,
 * so write throughput and lock waits under real checkout load remain unmeasured — see the note at
 * the foot of this file.
 *
 * The reads are not trivial ones, which is the point: best-sellers is a multi-join aggregate over
 * every paid order, and offer-quote resolves the effective offer, loads the variants, aggregates
 * units and allocates a discount per line. Both put real work and real connection-pool pressure
 * behind each request.
 *
 * Bounded by design: a fixed number of virtual users, a fixed duration, and no fixtures of its own —
 * it reads whatever the catalogue already holds and creates nothing.
 *
 * Usage:
 *   node loadtest/bounded-load.js --api http://localhost:8081/api --stages 5,10,20 --seconds 20
 */

const API = argValue("--api", "http://localhost:8081/api");
const STAGES = argValue("--stages", "5,10,20").split(",").map(Number);
const SECONDS = Number(argValue("--seconds", "20"));

function argValue(flag, fallback) {
  const index = process.argv.indexOf(flag);
  return index > -1 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

/** Cookie-jar + CSRF client, mirroring what the browser does. */
class Client {
  constructor() { this.cookies = new Map(); }
  cookieHeader() { return [...this.cookies].map(([k, v]) => `${k}=${v}`).join("; "); }
  absorb(response) {
    const raw = typeof response.headers.getSetCookie === "function" ? response.headers.getSetCookie() : [];
    for (const entry of raw) {
      const [pair] = entry.split(";");
      const index = pair.indexOf("=");
      if (index > 0) this.cookies.set(pair.slice(0, index).trim(), pair.slice(index + 1).trim());
    }
  }
  async request(method, path, body) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (!["GET", "HEAD"].includes(method)) {
      const csrf = await fetch(`${API}/auth/csrf`, { headers: { Cookie: this.cookieHeader() } });
      this.absorb(csrf);
      headers["X-XSRF-TOKEN"] = (await csrf.json()).token;
    }
    const cookie = this.cookieHeader();
    if (cookie) headers.Cookie = cookie;
    const started = process.hrtime.bigint();
    const response = await fetch(`${API}${path}`, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
    });
    this.absorb(response);
    const text = await response.text().catch(() => "");
    const ms = Number(process.hrtime.bigint() - started) / 1e6;
    return { status: response.status, ok: response.ok, ms, body: text ? safeJson(text) : null };
  }
  get(path) { return this.request("GET", path); }
  post(path, body) { return this.request("POST", path, body); }
}

const safeJson = (text) => { try { return JSON.parse(text); } catch { return null; } };
const percentile = (sorted, p) => (sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))] : 0);

/**
 * One virtual user: browse, price a bag, read the cart — the realistic mix.
 *
 * offer-quote is here because it is the busiest endpoint the automatic quantity offer added: the
 * storefront re-prices the bag on every quantity change, so it runs far more often than checkout
 * does. It is also the one that does real work per call — resolve the effective offer, load the
 * variants, aggregate, allocate — so leaving it out would measure everything except the feature.
 *
 * It stays a POST of quantities: the endpoint returns prices and writes nothing, so adding it does
 * not turn a read-dominant test into one that mutates the database.
 */
async function virtualUser(deadline, stats, productId, variantId, quoteQuantity) {
  const client = new Client();
  const steps = [
    ["catalog", () => client.get("/products?size=24&sort=productId,desc")],
    ["bestsellers", () => client.get("/products/best-sellers?limit=5")],
    ["product", () => client.get(`/products/${productId}`)],
    ["cart-read", () => client.get("/cart")],
  ];
  if (variantId != null && quoteQuantity >= 2) {
    // Enough units to complete at least one group, so the aggregation and allocation paths run
    // rather than short-circuiting on a cart that earns nothing. The caller picks the quantity from
    // real stock — see the fixture note below.
    steps.push(["offer-quote", () => client.post("/offers/automatic/quote",
      { lines: [{ variantId, quantity: quoteQuantity }] })]);
  }
  while (Date.now() < deadline) {
    for (const [label, call] of steps) {
      if (Date.now() >= deadline) break;
      try {
        const result = await call();
        record(stats, label, result);
      } catch (error) {
        stats.errors.push(`${label}: ${String(error).slice(0, 80)}`);
      }
    }
  }
}

function record(stats, label, result) {
  stats.latencies.push(result.ms);
  stats.total += 1;
  const bucket = stats.byOperation[label] || (stats.byOperation[label] = { n: 0, ms: [] });
  bucket.n += 1;
  bucket.ms.push(result.ms);
  // A guest cart legitimately answers 401; anything else non-2xx is an error for this purpose.
  if (!result.ok && !(label === "cart-read" && result.status === 401)) {
    stats.failed += 1;
    if (stats.errors.length < 10) stats.errors.push(`${label} -> ${result.status}`);
  }
}

(async () => {
  const probe = new Client();
  const catalogue = await probe.get("/products?size=1");
  if (!catalogue.ok || !catalogue.body?.content?.length) {
    console.error(`Cannot reach a catalogue at ${API} (status ${catalogue.status}). Is the backend up?`);
    process.exit(1);
  }
  // A product that can actually be priced, not simply the first one listed.
  //
  // The first product in this schema is a leftover fixture whose variants were all deactivated, so
  // taking content[0] gave a product with no variants — and the offer-quote step then quietly
  // skipped itself for the whole run while still reporting a clean result. A load test that silently
  // stops exercising what it was extended to measure is worse than one that fails, so a run with no
  // priceable product now says so in the header instead.
  // The quote endpoint excludes any line it could not fulfil, so the quantity asked for has to be
  // stock the chosen variant actually holds — otherwise the request succeeds while pricing nothing.
  // Pick the deepest-stocked active variant on the page and quote what it can supply, up to seven
  // (three groups plus a remainder, which is the shape that exercises grouping and allocation).
  const page = await probe.get("/products?size=50");
  let best = null;
  for (const product of page.body?.content || []) {
    for (const variant of product.variants || []) {
      const stock = Number(variant.quantityAvailable);
      if (variant.isActive !== false && stock >= 2
          && (best === null || stock > best.stock)) {
        best = { productId: product.productId, variantId: variant.variantId, stock };
      }
    }
  }
  const productId = best?.productId ?? catalogue.body.content[0].productId;
  const variantId = best?.variantId ?? null;
  const quoteQuantity = best ? Math.min(7, best.stock) : 0;
  const offer = await probe.get("/offers/automatic/active");
  console.log(`target=${API}  product=${productId}  `
    + `variant=${variantId ?? "NONE FOUND - offer-quote step will be skipped"}`
    + `${variantId ? ` x${quoteQuantity}` : ""}  `
    + `offer=${offer.body?.active ? offer.body.offerName : "none active"}  `
    + `stages=[${STAGES}]  seconds/stage=${SECONDS}\n`);
  console.log("vusers |  reqs |  rps  |  p50 |  p95 |  p99 |  max | errors");
  console.log("-------+-------+-------+------+------+------+------+-------");

  for (const vusers of STAGES) {
    const stats = { latencies: [], errors: [], total: 0, failed: 0, byOperation: {} };
    const deadline = Date.now() + SECONDS * 1000;
    const started = Date.now();
    await Promise.all(Array.from({ length: vusers }, () => virtualUser(deadline, stats, productId, variantId, quoteQuantity)));
    const elapsed = (Date.now() - started) / 1000;
    const sorted = [...stats.latencies].sort((a, b) => a - b);
    console.log(
      `${String(vusers).padStart(6)} |${String(stats.total).padStart(6)} |`
      + `${(stats.total / elapsed).toFixed(1).padStart(6)} |`
      + `${percentile(sorted, 0.5).toFixed(0).padStart(5)} |`
      + `${percentile(sorted, 0.95).toFixed(0).padStart(5)} |`
      + `${percentile(sorted, 0.99).toFixed(0).padStart(5)} |`
      + `${(sorted[sorted.length - 1] || 0).toFixed(0).padStart(5)} |`
      + `${String(stats.failed).padStart(6)}`
    );
    if (stats.errors.length) console.log(`         first errors: ${stats.errors.slice(0, 3).join(" | ")}`);
  }
  console.log("\nLatencies are milliseconds, measured client-side, so they include this process's own overhead.");
})();

// Not measured here, and worth knowing before trusting these numbers as a capacity statement:
//
//  - No authenticated cart write and no checkout, so write throughput, row-lock waits during
//    inventory decrement and transaction duration under real purchase load are all unmeasured.
//    Each virtual user would need its own verified account, and POST /api/auth/register is capped at
//    10 per IP per minute, so a write-heavy run needs accounts seeded ahead of time rather than
//    created inline.
//  - One machine, one process, localhost. Network latency, TLS and any proxy in front of the app are
//    all absent, and the client's own overhead is inside the latencies reported.
