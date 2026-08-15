package com.sunglassstore.concurrency;

import com.sunglassstore.dto.request.CartItemRequest;
import com.sunglassstore.dto.request.CreateOrderRequest;
import com.sunglassstore.entity.Address;
import com.sunglassstore.entity.Product;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.User;
import com.sunglassstore.repository.AddressRepository;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.repository.ProductVariantRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.CartService;
import com.sunglassstore.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inventory correctness under genuine parallelism, against the real database engine.
 *
 * Runs on the "mysql" profile, NOT the default H2 one. The property under test is a database
 * property: only InnoDB can demonstrate that {@code SELECT ... FOR UPDATE} serialises two
 * concurrent decrements. A green run against H2 would say nothing about production, which is why
 * the pre-existing CartConcurrencyIntegrationTest — which does run on H2 — is not evidence here.
 *
 * Opt-in via -Dconcurrency.tests=true so the normal build does not require a running MySQL.
 *
 * The invariant in every case: QUANTITY_AVAILABLE never goes negative, and units are conserved —
 * every unit that leaves stock corresponds to exactly one successful purchase.
 */
@SpringBootTest
@ActiveProfiles("mysql")
@EnabledIfSystemProperty(named = "concurrency.tests", matches = "true")
class CheckoutConcurrencyIntegrationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;

    private ProductVariant variant;

    @BeforeEach
    void seed() {
        Product product = new Product();
        product.setProductName("Concurrency Fixture " + System.nanoTime());
        product.setBrand("Shades World");
        product.setBasePrice(new BigDecimal("1000.00"));
        product.setIsActive(true);
        productRepository.saveAndFlush(product);

        variant = new ProductVariant();
        variant.setProduct(product);
        variant.setPosition(1);
        variant.setSku("CONC-" + System.nanoTime());
        variant.setVariantName("Onyx");
        variant.setPrice(new BigDecimal("1000.00"));
        variant.setQuantityAvailable(5);
        variant.setLowStockThreshold(1);
        variant.setIsActive(true);
        variantRepository.saveAndFlush(variant);
    }

    private User customer(String label) {
        User user = new User();
        user.setName("Concurrency " + label);
        user.setEmail("conc." + label + "." + System.nanoTime() + "@example.test");
        user.setPasswordHash("not-a-real-hash");
        user.setIsActive(true);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private Address addressFor(User user) {
        Address address = new Address();
        address.setUser(user);
        address.setAddressType(com.sunglassstore.entity.enums.AddressType.SHIPPING);
        address.setRecipientName(user.getName());
        address.setAddressLine1("1 Concurrency Street");
        address.setCity("Bengaluru");
        address.setState("Karnataka");
        address.setPincode("560001");
        address.setCountry("India");
        address.setIsDefault(true);
        return addressRepository.saveAndFlush(address);
    }

    /** Releases every task at the same instant and counts the successes. */
    private int runInParallel(List<Callable<Boolean>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (Callable<Boolean> task : tasks) {
                futures.add(pool.submit(() -> {
                    // The latch is what makes this a race rather than a sequence.
                    start.await();
                    try {
                        if (Boolean.TRUE.equals(task.call())) succeeded.incrementAndGet();
                    } catch (Exception losing) {
                        // Insufficient stock, a deadlock victim or a lock timeout are all correct
                        // ways to LOSE this race. What must never happen is a wrong total, which
                        // the conservation assertion below checks independently of who won.
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(120, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        return succeeded.get();
    }

    private int remainingStock() {
        return variantRepository.findById(variant.getVariantId()).orElseThrow().getQuantityAvailable();
    }

    @Test
    @DisplayName("ten customers racing for five units: stock never goes negative and units are conserved")
    void concurrentPurchasesCannotOversell() throws Exception {
        int stock = variant.getQuantityAvailable();
        List<Callable<Boolean>> buyers = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            User user = customer("buy" + index);
            Address address = addressFor(user);
            buyers.add(() -> {
                CartItemRequest item = new CartItemRequest();
                item.setVariantId(variant.getVariantId());
                item.setQuantity(1);
                cartService.addItem(user.getUserId(), item);

                CreateOrderRequest order = new CreateOrderRequest();
                order.setShippingAddressId(address.getAddressId());
                orderService.createOrder(user.getUserId(), order);
                return true;
            });
        }

        int succeeded = runInParallel(buyers);

        int remaining = remainingStock();
        assertTrue(remaining >= 0, "stock must never go negative, was " + remaining);
        assertTrue(succeeded <= stock, succeeded + " customers succeeded against " + stock + " units");
        assertEquals(stock, succeeded + remaining,
                "units must be conserved: every unit is either sold exactly once or still in stock");
    }

    @Test
    @DisplayName("the same cart submitted four times at once removes at most what was in it")
    void duplicateSubmissionsDoNotDoubleDecrement() throws Exception {
        User user = customer("dupe");
        Address address = addressFor(user);
        CartItemRequest item = new CartItemRequest();
        item.setVariantId(variant.getVariantId());
        item.setQuantity(2);
        cartService.addItem(user.getUserId(), item);
        int stock = variant.getQuantityAvailable();

        List<Callable<Boolean>> submissions = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            submissions.add(() -> {
                CreateOrderRequest order = new CreateOrderRequest();
                order.setShippingAddressId(address.getAddressId());
                orderService.createOrder(user.getUserId(), order);
                return true;
            });
        }

        runInParallel(submissions);

        int remaining = remainingStock();
        assertTrue(remaining >= 0, "stock must never go negative, was " + remaining);
        assertTrue(stock - remaining <= 2,
                "four simultaneous submissions of a 2-unit cart removed " + (stock - remaining) + " units");
    }
}
