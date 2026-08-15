package com.sunglassstore.service;

import com.sunglassstore.dto.request.CartItemRequest;
import com.sunglassstore.dto.response.CartResponse;
import com.sunglassstore.entity.Product;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.User;
import com.sunglassstore.repository.CartRepository;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.repository.ProductVariantRepository;
import com.sunglassstore.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cart-concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@ActiveProfiles("test")
class CartConcurrencyIntegrationTest {

    @Autowired private CartService cartService;
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private CartRepository carts;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Long createdProductId;

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(userId -> carts.findAll().stream()
                .filter(cart -> cart.getUser().getUserId().equals(userId)).forEach(carts::delete));
        if (createdProductId != null) products.deleteById(createdProductId);
        createdUserIds.forEach(users::deleteById);
    }

    @Test
    void concurrentAddsKeepUsersAndVariantsIndependentWithoutLostUpdates() throws Exception {
        User first = saveUser("first");
        User second = saveUser("second");
        Product product = new Product();
        product.setProductName("Concurrency frame");
        product.setBrand("Shades World");
        product.setBasePrice(new BigDecimal("1000.00"));
        product = products.save(product);
        createdProductId = product.getProductId();
        ProductVariant blue = saveVariant(product, "BLUE", 1);
        ProductVariant pink = saveVariant(product, "PINK", 2);

        int additionsPerUser = 12;
        CountDownLatch ready = new CountDownLatch(additionsPerUser * 2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(additionsPerUser * 2);
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < additionsPerUser; i++) {
            long firstVariant = i % 2 == 0 ? blue.getVariantId() : pink.getVariantId();
            long secondVariant = i % 2 == 0 ? pink.getVariantId() : blue.getVariantId();
            tasks.add(pool.submit(() -> addAfterSignal(first.getUserId(), firstVariant, ready, start)));
            tasks.add(pool.submit(() -> addAfterSignal(second.getUserId(), secondVariant, ready, start)));
        }
        ready.await();
        start.countDown();
        for (Future<?> task : tasks) task.get();
        pool.shutdown();

        assertCart(first.getUserId(), blue.getVariantId(), pink.getVariantId(), 6);
        assertCart(second.getUserId(), blue.getVariantId(), pink.getVariantId(), 6);
    }

    private void addAfterSignal(Long userId, Long variantId, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
            CartItemRequest request = new CartItemRequest();
            request.setVariantId(variantId);
            request.setQuantity(1);
            cartService.addItem(userId, request);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void assertCart(Long userId, Long blueId, Long pinkId, int expectedEach) {
        CartResponse cart = cartService.getOrCreateCart(userId);
        assertEquals(2, cart.items().size());
        assertEquals(expectedEach, quantity(cart, blueId));
        assertEquals(expectedEach, quantity(cart, pinkId));
    }

    private int quantity(CartResponse cart, Long variantId) {
        return cart.items().stream().filter(line -> line.variantId().equals(variantId))
                .findFirst().orElseThrow().quantity();
    }

    private User saveUser(String prefix) {
        User user = new User();
        user.setEmail(prefix + "+" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-only");
        user.setName(prefix);
        user = users.save(user);
        createdUserIds.add(user.getUserId());
        return user;
    }

    private ProductVariant saveVariant(Product product, String color, int position) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setPosition(position);
        variant.setSku("E2E-" + color + "-" + UUID.randomUUID());
        variant.setVariantName(color);
        variant.setPrice(new BigDecimal("1000.00"));
        variant.setQuantityAvailable(100);
        return variants.save(variant);
    }
}
