package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateOrderRequest;
import com.sunglassstore.entity.*;
import com.sunglassstore.entity.enums.CartStatus;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.PaymentStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.repository.*;
import com.sunglassstore.service.impl.OrderServiceImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceImplTest {
    private OrderRepository orders;
    private UserRepository users;
    private OrderStatusHistoryRepository histories;
    private CartRepository carts;
    private AddressRepository addresses;
    private ProductVariantRepository variants;
    private CouponRepository coupons;
    private CouponUsageRepository couponUsages;
    private InventoryMovementRepository movements;
    private PaymentRepository payments;
    private ShipmentRepository shipments;
    private RefundRepository refunds;
    private CouponService couponService;
    private com.sunglassstore.service.AutomaticOfferService automaticOffers;
    private ApplicationEventPublisher events;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class); users = mock(UserRepository.class);
        histories = mock(OrderStatusHistoryRepository.class); carts = mock(CartRepository.class);
        addresses = mock(AddressRepository.class); variants = mock(ProductVariantRepository.class);
        coupons = mock(CouponRepository.class); couponUsages = mock(CouponUsageRepository.class);
        movements = mock(InventoryMovementRepository.class); payments = mock(PaymentRepository.class);
        shipments = mock(ShipmentRepository.class); refunds = mock(RefundRepository.class);
        couponService = mock(CouponService.class); events = mock(ApplicationEventPublisher.class);
        automaticOffers = mock(com.sunglassstore.service.AutomaticOfferService.class);
        // No automatic offer in force for these tests: they cover locking, pricing from the locked
        // variant, and inventory. The offer's own effect on a checkout is tested against a real
        // database in AutomaticOfferOrderIntegrationTest, where an in-force offer is a row rather
        // than a stubbed return value.
        when(automaticOffers.effectiveOffer(any())).thenReturn(java.util.Optional.empty());
        when(automaticOffers.priceLines(any(), any()))
                .thenReturn(com.sunglassstore.offer.AutomaticOfferPricing.NONE);
        service = new OrderServiceImpl(orders, users, histories, carts, addresses, variants,
                coupons, couponUsages, movements, payments, shipments, refunds, couponService,
                automaticOffers, events);
    }

    @Test
    void checkoutUsesLockedCurrentPriceAndAtomicallyDeductsInventory() {
        Fixture fixture = fixture();
        stubCheckout(fixture);

        Order order = service.createOrder(7L, request(20L, null));

        assertEquals(new BigDecimal("240.00"), order.getSubtotalAmount());
        // Tax-inclusive: the ₹240 of merchandise IS ₹240 to the customer (₹203.39 net + ₹36.61
        // GST), plus ₹49 carriage below the free-shipping threshold. Additively this was ₹332.20.
        assertEquals(new BigDecimal("289.00"), order.getTotalAmount());
        assertEquals(new BigDecimal("120.00"), order.getItems().getFirst().getUnitPrice());
        assertEquals(3, fixture.lockedVariant.getQuantityAvailable());
        assertSame(fixture.address, order.getShippingAddress());
        assertSame(fixture.address, order.getBillingAddress());
        assertEquals(CartStatus.ORDERED, fixture.cart.getCartStatus());
        verify(variants).findByIdForUpdate(11L);
        verify(variants, never()).findById(11L);
        verify(movements).save(argThat(movement -> movement.getQuantityChange() == -2));
        verify(carts).save(argThat(cart -> cart != fixture.cart && cart.getCartStatus() == CartStatus.ACTIVE));
    }

    @Test
    void checkoutRejectsBillingAddressOwnedByAnotherCustomerBeforeCreatingOrder() {
        Fixture fixture = fixture();
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(fixture.user));
        when(carts.findByUserUserIdAndCartStatusForUpdate(7L, CartStatus.ACTIVE)).thenReturn(Optional.of(fixture.cart));
        when(variants.findByIdForUpdate(11L)).thenReturn(Optional.of(fixture.lockedVariant));
        when(addresses.findByAddressIdAndUserUserId(20L, 7L)).thenReturn(Optional.of(fixture.address));
        when(addresses.findByAddressIdAndUserUserId(99L, 7L)).thenReturn(Optional.empty());

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createOrder(7L, request(20L, 99L)));

        assertTrue(error.getMessage().contains("Billing address"));
        verify(orders, never()).save(any());
        verifyNoInteractions(movements);
    }

    @Test
    void checkoutPersistsAuthoritativeCouponDiscountAndLocksCoupon() {
        Fixture fixture = fixture(); stubCheckout(fixture);
        Coupon coupon = new Coupon(); coupon.setCouponId(5L); coupon.setCouponCode("PAIR500");
        when(coupons.findByCouponCodeIgnoreCaseForUpdate("PAIR500")).thenReturn(Optional.of(coupon));
        when(couponService.calculateDiscount(coupon, new BigDecimal("240.00"), 2))
                .thenReturn(new BigDecimal("50.00"));
        CreateOrderRequest request = request(20L, null); request.setCouponCode(" PAIR500 ");
        request.setExpectedTotalAmount(new BigDecimal("239.00"));

        Order order = service.createOrder(7L, request);

        assertEquals(new BigDecimal("50.00"), order.getDiscountAmount());
        verify(couponUsages).save(argThat(usage -> usage.getDiscountAmount().compareTo(new BigDecimal("50.00")) == 0));
        verify(coupons).findByCouponCodeIgnoreCaseForUpdate("PAIR500");
    }

    @Test
    void paidOrderCancellationRefundsRemainingBalanceAndRestoresInventory() {
        Fixture fixture = fixture();
        Order order = new Order(); order.setOrderId(30L); order.setUser(fixture.user);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        OrderItem item = new OrderItem(); item.setVariant(fixture.lockedVariant); item.setQuantity(2);
        order.setItems(List.of(item));
        Payment payment = new Payment(); payment.setPaymentId(40L); payment.setOrder(order);
        payment.setAmount(new BigDecimal("289.00")); payment.setPaymentStatus(PaymentStatus.PAID);
        when(orders.findByOrderIdAndUserUserIdForUpdate(30L, 7L)).thenReturn(Optional.of(order));
        when(payments.findByOrderOrderIdOrderByCreatedAtDesc(30L)).thenReturn(List.of(payment));
        when(payments.findByIdForUpdate(40L)).thenReturn(Optional.of(payment));
        when(refunds.sumRefundedByPaymentId(40L)).thenReturn(new BigDecimal("20.00"));
        when(variants.findByIdForUpdate(11L)).thenReturn(Optional.of(fixture.lockedVariant));
        when(orders.save(order)).thenReturn(order);

        Order cancelled = service.cancelOrder(7L, 30L);

        assertEquals(OrderStatus.CANCELLED, cancelled.getOrderStatus());
        assertEquals(PaymentStatus.REFUNDED, payment.getPaymentStatus());
        assertEquals(7, fixture.lockedVariant.getQuantityAvailable());
        verify(refunds).save(argThat(refund -> refund.getReturnRequest() == null
                && refund.getRefundAmount().compareTo(new BigDecimal("269.00")) == 0
                && refund.getRefundStatus() == com.sunglassstore.entity.enums.RefundStatus.COMPLETED));
        verify(movements, times(1)).save(any());
        verify(events).publishEvent(any(com.sunglassstore.email.event.OrderCancelledEmailRequested.class));
    }

    @Test
    void cancellationCannotRunTwiceOrRestoreInventoryTwice() {
        Order order = new Order(); order.setOrderId(30L); order.setOrderStatus(OrderStatus.CANCELLED);
        when(orders.findByOrderIdAndUserUserIdForUpdate(30L, 7L)).thenReturn(Optional.of(order));
        assertThrows(BadRequestException.class, () -> service.cancelOrder(7L, 30L));
        verifyNoInteractions(refunds, movements);
    }

    @Test
    void checkoutRejectsChangedTotalBeforeCreatingOrderOrDeductingStock() {
        Fixture fixture = fixture(); stubCheckout(fixture);
        CreateOrderRequest request = request(20L, null);
        request.setExpectedTotalAmount(new BigDecimal("300.00"));
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createOrder(7L, request));
        assertTrue(error.getMessage().contains("total changed"));
        verify(orders, never()).save(any());
        verifyNoInteractions(movements);
        assertEquals(5, fixture.lockedVariant.getQuantityAvailable());
    }

    @Test
    void adminCancellationOfUnpaidOrderRestoresInventoryOnce() {
        Fixture fixture = fixture();
        Order order = new Order(); order.setOrderId(30L); order.setUser(fixture.user);
        order.setOrderStatus(OrderStatus.PLACED);
        OrderItem item = new OrderItem(); item.setOrder(order); item.setVariant(fixture.lockedVariant); item.setQuantity(2);
        order.setItems(List.of(item));
        when(orders.findByIdForUpdate(30L)).thenReturn(Optional.of(order));
        when(payments.existsByOrderOrderIdAndPaymentStatusIn(eq(30L), any())).thenReturn(false);
        when(variants.findByIdForUpdate(11L)).thenReturn(Optional.of(fixture.lockedVariant));
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateOrderStatus(30L, OrderStatus.CANCELLED, "Customer request");

        assertEquals(7, fixture.lockedVariant.getQuantityAvailable());
        verify(movements, times(1)).save(any());
    }

    private void stubCheckout(Fixture fixture) {
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(fixture.user));
        when(carts.findByUserUserIdAndCartStatusForUpdate(7L, CartStatus.ACTIVE)).thenReturn(Optional.of(fixture.cart));
        when(variants.findByIdForUpdate(11L)).thenReturn(Optional.of(fixture.lockedVariant));
        when(addresses.findByAddressIdAndUserUserId(20L, 7L)).thenReturn(Optional.of(fixture.address));
        when(orders.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0); order.setOrderId(30L); return order;
        });
        when(carts.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Fixture fixture() {
        User user = new User(); user.setUserId(7L); user.setName("Customer");
        Product product = new Product(); product.setProductId(3L); product.setProductName("Barcelona"); product.setIsActive(true);
        ProductVariant stale = new ProductVariant(); stale.setVariantId(11L); stale.setProduct(product);
        stale.setSku("BAR-BLK"); stale.setPrice(new BigDecimal("10.00")); stale.setQuantityAvailable(5);
        ProductVariant locked = new ProductVariant(); locked.setVariantId(11L); locked.setProduct(product);
        locked.setSku("BAR-BLK"); locked.setPrice(new BigDecimal("120.00")); locked.setQuantityAvailable(5); locked.setIsActive(true);
        Cart cart = new Cart(); cart.setCartId(9L); cart.setUser(user); cart.setCartStatus(CartStatus.ACTIVE);
        CartItem item = new CartItem(); item.setCart(cart); item.setVariant(stale); item.setQuantity(2); cart.setItems(List.of(item));
        Address address = new Address(); address.setAddressId(20L); address.setUser(user);
        address.setRecipientName("Customer"); address.setAddressLine1("Street 1"); address.setCity("Barcelona");
        address.setState("Catalonia"); address.setPincode("08001"); address.setCountry("Spain");
        return new Fixture(user, cart, locked, address);
    }

    private CreateOrderRequest request(Long shippingId, Long billingId) {
        CreateOrderRequest request = new CreateOrderRequest(); request.setShippingAddressId(shippingId);
        request.setBillingAddressId(billingId); request.setExpectedTotalAmount(new BigDecimal("289.00")); return request;
    }

    private record Fixture(User user, Cart cart, ProductVariant lockedVariant, Address address) {}
}
