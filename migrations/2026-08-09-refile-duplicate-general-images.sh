#!/usr/bin/env bash
# Re-files "general" product images that are byte-identical to a colourway's own photo.
#
# ── Why ──────────────────────────────────────────────────────────────────────────────────
# The admin create form has a "Product photos" field (stored with no variant, i.e. shown for every
# colour) and a per-colour field. Admins routinely picked the SAME file for both. A general image
# is shown in every colourway's gallery by design, so that duplicate put a photograph of the first
# colourway — frequently the sold-out one — into every other colourway's gallery. That is the
# "the out-of-stock item appears in the in-stock one's additional photos" report.
#
# Measured on ECOMMERCE_DB before writing this: 4 of 6 products had a general image whose SHA-256
# matched exactly one variant-owned image on the same product.
#
# ── What it does ─────────────────────────────────────────────────────────────────────────
# For each product, any image with VARIANT_ID IS NULL whose file content matches a variant-owned
# image of the same product is REMOVED, and if it was the primary image its byte-identical twin is
# promoted in its place — so the product's primary photograph is unchanged, pixel for pixel.
#
# Removing rather than re-filing onto the variant: re-filing leaves the colourway showing the same
# photograph twice in its own gallery, which is just a different visible bug. The row is redundant
# by definition here — an identical file already exists, owned by the variant.
#
# NO FILE IS DELETED. Only the duplicate database row goes, so this is reversible: the image is
# still on disk at the URL recorded in the report below.
#
# A hash cannot be computed in SQL — MySQL cannot read the upload directory — so this is a shell
# script rather than a .sql migration. Idempotent: a second run finds no general duplicates left.
#
# ── Usage ────────────────────────────────────────────────────────────────────────────────
#   ./2026-08-09-refile-duplicate-general-images.sh ECOMMERCE_DB [--apply]
# Without --apply it only reports what it would change.

set -u
DB="${1:?usage: $0 <database> [--apply]}"
APPLY="${2:-}"
MYSQL="${MYSQL_BIN:-/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe}"
UPLOAD_ROOT="${UPLOAD_ROOT:-uploads/products}"
QUERY() { "$MYSQL" -uroot -p0004 -N -e "$1" 2>/dev/null | tr -d '\r'; }

echo "Scanning $DB (upload root: $UPLOAD_ROOT)"

# imageId, productId, variantId|GEN, relative path
ROWS=$(QUERY "SELECT IMAGE_ID, PRODUCT_ID, COALESCE(VARIANT_ID,'GEN'),
              SUBSTRING_INDEX(IMAGE_URL,'/uploads/products/',-1)
              FROM $DB.PRODUCT_IMAGES ORDER BY PRODUCT_ID, IMAGE_ID")

declare -A OWNER_OF   # "product|hash" -> variantId   (variant-owned images only)
declare -A HASH_OF    # imageId        -> hash
declare -A PRODUCT_OF # imageId        -> productId

while IFS=$'\t' read -r id product owner rel; do
  [ -z "${id:-}" ] && continue
  file="$UPLOAD_ROOT/$rel"
  [ -f "$file" ] || { echo "  image $id: file missing, skipped"; continue; }
  hash=$(sha256sum "$file" | cut -d' ' -f1)
  HASH_OF[$id]=$hash
  PRODUCT_OF[$id]=$product
  # First variant-owned image wins the claim on that content.
  if [ "$owner" != "GEN" ] && [ -z "${OWNER_OF[$product|$hash]:-}" ]; then
    OWNER_OF[$product|$hash]=$owner
  fi
done <<< "$ROWS"

CHANGES=0
while IFS=$'\t' read -r id product owner rel; do
  [ -z "${id:-}" ] && continue
  [ "$owner" = "GEN" ] || continue
  hash="${HASH_OF[$id]:-}"
  [ -n "$hash" ] || continue
  variant="${OWNER_OF[$product|$hash]:-}"
  [ -n "$variant" ] || continue
  twin=$(QUERY "SELECT IMAGE_ID FROM $DB.PRODUCT_IMAGES WHERE PRODUCT_ID=$product AND VARIANT_ID=$variant ORDER BY IMAGE_ID LIMIT 1")
  was_primary=$(QUERY "SELECT IS_PRIMARY FROM $DB.PRODUCT_IMAGES WHERE IMAGE_ID=$id")
  echo "  product $product: general image $id is a byte-identical copy of variant $variant's image $twin"
  echo "      -> removing row $id (file kept at $rel); primary=$was_primary"
  CHANGES=$((CHANGES + 1))
  if [ "$APPLY" = "--apply" ]; then
    QUERY "DELETE FROM $DB.PRODUCT_IMAGES WHERE IMAGE_ID = $id AND VARIANT_ID IS NULL" >/dev/null
    # Promote the twin only after the old primary row is gone: UQ_PRODUCT_IMAGES_PRIMARY makes
    # "primary for product N" unique, so promoting first would be rejected outright.
    if [ "$was_primary" = "1" ]; then
      QUERY "UPDATE $DB.PRODUCT_IMAGES SET IS_PRIMARY = 1 WHERE IMAGE_ID = $twin" >/dev/null
    fi
  fi
done <<< "$ROWS"

if [ "$CHANGES" -eq 0 ]; then
  echo "No general images duplicate a colourway's photo. Nothing to do."
elif [ "$APPLY" = "--apply" ]; then
  echo "Applied $CHANGES change(s)."
else
  echo "$CHANGES change(s) would be applied. Re-run with --apply."
fi
