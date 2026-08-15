package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateOrderRequest;
import com.sunglassstore.dto.response.AdminOrderResponse;
import com.sunglassstore.entity.*;
import com.sunglassstore.entity.enums.*;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.InsufficientInventoryException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.offer.AutomaticOfferPricing;
import com.sunglassstore.offer.MerchandisePromotionPolicy;
import com.sunglassstore.offer.OrderTotals;
import com.sunglassstore.repository.*;
import com.sunglassstore.service.AutomaticOfferService;
import com.sunglassstore.service.CouponService;
import com.sunglassstore.service.OrderService;
import com.sunglassstore.email.event.OrderCancelledEmailRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final RefundRepository refundRepository;
    private final CouponService couponService;
    private final AutomaticOfferService automaticOfferService;
    private final ApplicationEventPublisher eventPublisher;

    // Tax, shipping and total now live in OrderTotals, because the cart quote has to produce the
    // same number this method will compute — checkout sends it back as expectedTotalAmount and a
    // paisa of disagreement rejects a correct order. One copy of the rules, two callers.

    @Override
    @Transactional
    public Order createOrder(Long userId, CreateOrderRequest request) {

        // Idempotency first, before any lock is taken or any stock is touched. A retried checkout
        // must be a read, not a second attempt at the whole transaction.
        String idempotencyKey = request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()
                ? null : request.getIdempotencyKey().trim();
        if (idempotencyKey != null) {
            Optional<Order> alreadyPlaced =
                    orderRepository.findByIdempotencyKeyAndUserUserId(idempotencyKey, userId);
            if (alreadyPlaced.isPresent()) {
                return alreadyPlaced.get();
            }
        }

        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Step 1: Load the authenticated user's active cart
        Cart cart = cartRepository.findByUserUserIdAndCartStatusForUpdate(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("No active cart found"));

        // Step 2: Confirm the cart is not empty
        List<CartItem> cartItems = cart.getItems();
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        // Lock every variant in a stable order before reading prices or stock. This prevents
        // overselling, stale-price orders, and deadlocks between multi-item checkouts.
        List<CartItem> sortedCartItems = new ArrayList<>(cartItems);
        sortedCartItems.sort(Comparator.comparing(item -> item.getVariant().getVariantId()));
        Map<Long, ProductVariant> lockedVariants = new HashMap<>();
        for (CartItem item : sortedCartItems) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Cart contains an invalid quantity");
            }
            Long variantId = item.getVariant().getVariantId();
            ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                    .orElseThrow(() -> new BadRequestException(
                            "Product variant no longer available: " + variantId));

            if (!Boolean.TRUE.equals(variant.getIsActive()) || !Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
                throw new BadRequestException("Product is no longer available: " + variant.getProduct().getProductName());
            }

            if (variant.getQuantityAvailable() < item.getQuantity()) {
                throw new InsufficientInventoryException(
                        "Insufficient stock for " + variant.getProduct().getProductName()
                                + " (SKU: " + variant.getSku() + "). Available: "
                                + variant.getQuantityAvailable() + ", Requested: " + item.getQuantity());
            }
            lockedVariants.put(variantId, variant);
        }

        // Step 5: Validate shipping address belongs to the user
        Address shippingAddress = addressRepository.findByAddressIdAndUserUserId(
                        request.getShippingAddressId(), userId)
                .orElseThrow(() -> new BadRequestException("Shipping address not found or does not belong to you"));
        Address billingAddress = request.getBillingAddressId() == null
                ? shippingAddress
                : addressRepository.findByAddressIdAndUserUserId(request.getBillingAddressId(), userId)
                    .orElseThrow(() -> new BadRequestException("Billing address not found or does not belong to you"));

        // Step 6: Validate coupon when supplied
        Coupon coupon = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            coupon = couponRepository.findByCouponCodeIgnoreCaseForUpdate(request.getCouponCode().trim())
                    .orElseThrow(() -> new BadRequestException("Invalid coupon code"));
        }

        // Step 7: Calculate subtotal, discount, tax, shipping, total
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItemQuantity = 0;
        for (CartItem item : sortedCartItems) {
            ProductVariant variant = lockedVariants.get(item.getVariant().getVariantId());
            BigDecimal price = variant.getPrice();
            subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
            totalItemQuantity += item.getQuantity();
        }

        if (coupon != null) {
            // Full validation via coupon service
            com.sunglassstore.dto.request.ValidateCouponRequest validateReq =
                    new com.sunglassstore.dto.request.ValidateCouponRequest();
            validateReq.setCouponCode(coupon.getCouponCode());
            couponService.validateCoupon(userId, validateReq);
            discountAmount = couponService.calculateDiscount(coupon, subtotal, totalItemQuantity);
        }

        // Step 7b: the automatic quantity offer.
        //
        // Recalculated here from the locked variants rather than trusted from the request, and
        // resolved against one instant for the whole transaction — an offer that expired while the
        // customer was on the checkout page is simply not effective at `pricedAt`, and the total
        // check below is what then stops the order rather than charging the stale amount.
        LocalDateTime pricedAt = LocalDateTime.now();
        Map<Long, Long> variantToProduct = new HashMap<>();
        for (CartItem item : sortedCartItems) {
            ProductVariant variant = lockedVariants.get(item.getVariant().getVariantId());
            variantToProduct.put(variant.getVariantId(), variant.getProduct().getProductId());
        }
        AutomaticOffer automaticOffer = automaticOfferService.effectiveOffer(pricedAt).orElse(null);
        Set<Long> eligibleVariantIds = automaticOffer == null
                ? Set.of()
                : automaticOfferService.eligibleVariantIds(automaticOffer, variantToProduct);
        List<AutomaticOfferPricing.Line> offerLines = new ArrayList<>();
        for (CartItem item : sortedCartItems) {
            ProductVariant variant = lockedVariants.get(item.getVariant().getVariantId());
            offerLines.add(new AutomaticOfferPricing.Line(variant.getVariantId(), item.getQuantity(),
                    variant.getPrice(), eligibleVariantIds.contains(variant.getVariantId())));
        }
        AutomaticOfferPricing.Result automaticResult =
                automaticOfferService.priceLines(automaticOffer, offerLines);

        // Step 7c: one promotion, decided centrally. Both discounts are fully computed first, so
        // which one the customer gets cannot depend on the order the request happened to be built in.
        MerchandisePromotionPolicy.Decision decision = MerchandisePromotionPolicy.decide(
                automaticResult.discount(),
                automaticOffer == null ? null : automaticOffer.getOfferName(),
                discountAmount,
                coupon == null ? null : "Coupon " + coupon.getCouponCode());

        boolean couponApplied = decision.applied() == MerchandisePromotionPolicy.AppliedPromotion.COUPON;
        boolean automaticApplied =
                decision.applied() == MerchandisePromotionPolicy.AppliedPromotion.AUTOMATIC_OFFER;
        discountAmount = decision.discount();
        if (!couponApplied) {
            // The coupon lost the comparison, so it is not recorded against the order and its usage
            // count is untouched: a customer must not spend a single-use coupon on an order that did
            // not use it.
            coupon = null;
        }

        OrderTotals totals = OrderTotals.of(subtotal, discountAmount);
        BigDecimal taxAmount = totals.tax();
        BigDecimal shippingAmount = totals.shipping();
        BigDecimal totalAmount = totals.total();
        if (request.getExpectedTotalAmount().setScale(2, RoundingMode.HALF_UP)
                .compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new BadRequestException("Your order total changed. Return to your bag, review the latest prices and offer, then try again.");
        }

        // Step 8: Create the order
        Order order = new Order();
        order.setUser(cart.getUser());
        order.setOrderStatus(OrderStatus.PLACED);
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(discountAmount);
        order.setTaxAmount(taxAmount);
        order.setShippingAmount(shippingAmount);
        order.setTotalAmount(totalAmount);
        order.setShippingAddress(shippingAddress);
        order.setBillingAddress(billingAddress);
        order.setPurchasedAt(LocalDateTime.now());
        order.setIdempotencyKey(idempotencyKey);
        if (coupon != null) {
            order.setCoupon(coupon);
        }
        // Immutable snapshot of the offer terms. Written only when the offer actually paid out, so
        // an order that lost the stacking comparison does not claim an offer it was not charged
        // under. Every figure the customer was quoted is copied, because the offer row may be
        // edited or archived tomorrow and this order's history must not move with it.
        if (automaticApplied) {
            order.setAutoOfferId(automaticOffer.getAutomaticOfferId());
            order.setAutoOfferName(automaticOffer.getOfferName());
            order.setAutoOfferRequiredQuantity(automaticOffer.getRequiredQuantity());
            order.setAutoOfferDiscountPerGroup(automaticOffer.getDiscountPerGroup());
            order.setAutoOfferEligibleQuantity(automaticResult.eligibleQuantity());
            order.setAutoOfferGroups(automaticResult.completeGroups());
            order.setAutoOfferDiscount(automaticResult.discount());
        }

        // Step 10: Copy shipping address values into order snapshot columns
        order.setShippingName(shippingAddress.getRecipientName());
        order.setShippingPhone(shippingAddress.getPhoneNumber());
        order.setShippingAddressLine1(shippingAddress.getAddressLine1());
        order.setShippingAddressLine2(shippingAddress.getAddressLine2());
        order.setShippingCity(shippingAddress.getCity());
        order.setShippingState(shippingAddress.getState());
        order.setShippingPincode(shippingAddress.getPincode());
        order.setShippingCountry(shippingAddress.getCountry());

        // Step 9: Create order items with snapshot data
        //
        // Each line also records its share of the order-level discount. That share is what a partial
        // return refunds against later: without it, refunding a returned unit would have to guess how
        // much of the discount belonged to it, and the only available guess — the list price — would
        // refund more than the customer paid.
        Map<Long, BigDecimal> lineDiscounts = automaticApplied
                ? automaticResult.lineDiscounts()
                : couponApplied ? allocateProportionally(sortedCartItems, lockedVariants, discountAmount)
                : Map.of();
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : sortedCartItems) {
            ProductVariant variant = lockedVariants.get(cartItem.getVariant().getVariantId());
            Product product = variant.getProduct();

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setVariant(variant);
            orderItem.setProductName(product.getProductName());
            orderItem.setSku(variant.getSku());
            // Same label precedence the storefront renders (colour attribute, else variant name),
            // frozen here so the line keeps reading the same after any later catalogue edit.
            orderItem.setVariantLabel(variant.getAttributes().stream()
                    .filter(attribute -> "color".equals(attribute.getAttributeName()))
                    .map(com.sunglassstore.entity.ProductAttribute::getAttributeValue)
                    .findFirst()
                    .orElse(variant.getVariantName()));
            orderItem.setUnitPrice(variant.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setLineTotal(variant.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            orderItem.setDiscountAmount(lineDiscounts.getOrDefault(variant.getVariantId(),
                    BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            orderItems.add(orderItem);
        }
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        // Step 11 & 12: Deduct the inventory rows already locked above and write movements.
        for (CartItem cartItem : sortedCartItems) {
            ProductVariant lockedVariant = lockedVariants.get(cartItem.getVariant().getVariantId());
            lockedVariant.setQuantityAvailable(lockedVariant.getQuantityAvailable() - cartItem.getQuantity());
            productVariantRepository.save(lockedVariant);

            InventoryMovement movement = new InventoryMovement();
            movement.setVariant(lockedVariant);
            movement.setMovementType(MovementType.SALE);
            movement.setQuantityChange(-cartItem.getQuantity());
            movement.setReferenceId(savedOrder.getOrderId());
            movement.setNotes("Order #" + savedOrder.getOrderId());
            inventoryMovementRepository.save(movement);
        }

        // Step 13: Create order status history
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(savedOrder);
        history.setNewStatus(OrderStatus.PLACED.name());
        history.setNotes("Order placed");
        orderStatusHistoryRepository.save(history);

        // Record coupon usage
        if (coupon != null) {
            CouponUsage usage = new CouponUsage();
            usage.setCoupon(coupon);
            usage.setUser(cart.getUser());
            usage.setOrder(savedOrder);
            usage.setDiscountAmount(discountAmount);
            couponUsageRepository.save(usage);
        }

        // Step 14: Mark the cart as ordered
        cart.setCartStatus(CartStatus.ORDERED);
        cartRepository.save(cart);

        // Step 15: Create a new empty active cart
        Cart newCart = new Cart();
        newCart.setUser(cart.getUser());
        newCart.setCartStatus(CartStatus.ACTIVE);
        cartRepository.save(newCart);

        return savedOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserUserIdOrderByPurchasedAtDesc(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getUserOrder(Long userId, Long orderId) {
        return orderRepository.findByOrderIdAndUserUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Override
    @Transactional
    public Order cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByOrderIdAndUserUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.PLACED &&
                order.getOrderStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order cannot be cancelled in status: " + order.getOrderStatus());
        }

        OrderStatus oldStatus = order.getOrderStatus();
        BigDecimal refundAmount = settlePaymentsForCancellation(order);
        order.setOrderStatus(OrderStatus.CANCELLED);

        restoreInventory(order);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus.name());
        history.setNewStatus(OrderStatus.CANCELLED.name());
        history.setNotes("Order cancelled by customer");
        orderStatusHistoryRepository.save(history);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderCancelledEmailRequested(order.getUser().getEmail(),
                order.getUser().getName(), order.getOrderId(), refundAmount));
        return saved;
    }

    /**
     * Expiry path for an abandoned checkout. Everything happens in one transaction so the
     * eligibility re-check and the cancellation cannot be separated: the row lock is taken first,
     * so a payment that arrives concurrently either committed before this lock (and is seen here,
     * leaving the order alone) or blocks until this transaction finishes.
     *
     * Stock restore is at-most-once because cancelOrder refuses anything not PLACED or CONFIRMED,
     * and this method leaves the order CANCELLED.
     */
    @Override
    @Transactional
    public boolean expireUnpaidOrder(Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getOrderStatus() != OrderStatus.PLACED) return false;
        if (paymentRepository.existsByOrderOrderIdAndPaymentStatusIn(orderId,
                List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED))) return false;
        cancelOrder(order.getUser().getUserId(), orderId);
        return true;
    }

    /**
     * Per-line shares of a coupon discount, so a coupon order records the same kind of allocation an
     * automatic-offer order does. A coupon has no eligibility scope, so every line shares it.
     */
    private Map<Long, BigDecimal> allocateProportionally(List<CartItem> items,
                                                         Map<Long, ProductVariant> lockedVariants,
                                                         BigDecimal discount) {
        List<AutomaticOfferPricing.Line> lines = new ArrayList<>();
        for (CartItem item : items) {
            ProductVariant variant = lockedVariants.get(item.getVariant().getVariantId());
            lines.add(new AutomaticOfferPricing.Line(variant.getVariantId(), item.getQuantity(),
                    variant.getPrice(), true));
        }
        return AutomaticOfferPricing.allocateAcross(lines, discount);
    }

    private void restoreInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            // A line whose product has since been deleted has no stock to restore — the variant row
            // is gone. Skipping it is the only correct action: the alternative is failing the whole
            // cancellation, which would trap the customer's money over a catalogue change that has
            // nothing to do with them. The order line itself is untouched and still refundable.
            if (item.getVariant() == null) continue;
            ProductVariant lockedVariant = productVariantRepository.findByIdForUpdate(
                    item.getVariant().getVariantId())
                    .orElseThrow(() -> new BadRequestException("Variant not found during cancellation"));

            lockedVariant.setQuantityAvailable(lockedVariant.getQuantityAvailable() + item.getQuantity());
            productVariantRepository.save(lockedVariant);

            InventoryMovement movement = new InventoryMovement();
            movement.setVariant(lockedVariant);
            movement.setMovementType(MovementType.CANCELLATION);
            movement.setQuantityChange(item.getQuantity());
            movement.setReferenceId(order.getOrderId());
            movement.setNotes("Order #" + order.getOrderId() + " cancelled");
            inventoryMovementRepository.save(movement);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAllByOrderByPurchasedAtDesc(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Override
    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus status, String note) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        OrderStatus oldStatus = order.getOrderStatus();
        validateTransition(oldStatus, status);
        if (status == OrderStatus.CANCELLED) {
            BigDecimal refundAmount = settlePaymentsForCancellation(order);
            restoreInventory(order);
            eventPublisher.publishEvent(new OrderCancelledEmailRequested(order.getUser().getEmail(),
                    order.getUser().getName(), order.getOrderId(), refundAmount));
        }
        order.setOrderStatus(status);
        if (status == OrderStatus.DELIVERED) order.setDeliveredAt(LocalDateTime.now());

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus.name());
        history.setNewStatus(status.name());
        history.setNotes(note);
        orderStatusHistoryRepository.save(history);

        return orderRepository.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> getAllOrdersForAdmin(Pageable pageable) {
        return orderRepository.findAllByOrderByPurchasedAtDesc(pageable).map(this::toAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> getUserOrdersForCustomer(Long userId, Pageable pageable) {
        return orderRepository.findByUserUserIdOrderByPurchasedAtDesc(userId, pageable)
                .map(this::toAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderResponse getOrderForAdmin(Long orderId) {
        return toAdminResponse(getOrderById(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderResponse getUserOrderForCustomer(Long userId, Long orderId) {
        return toAdminResponse(getUserOrder(userId, orderId));
    }

    @Override
    @Transactional
    public AdminOrderResponse updateOrderStatusForAdmin(Long orderId, OrderStatus status, String note) {
        return toAdminResponse(updateOrderStatus(orderId, status, note));
    }

    private AdminOrderResponse toAdminResponse(Order order) {
        Long id = order.getOrderId();
        return AdminOrderResponse.fromEntity(order,
                paymentRepository.findByOrderOrderIdOrderByCreatedAtDesc(id),
                shipmentRepository.findByOrderOrderIdOrderByCreatedAtDesc(id),
                orderStatusHistoryRepository.findByOrderOrderIdOrderByChangedAtAsc(id));
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
        if (current == next) throw new BadRequestException("Order is already in status " + current);
        boolean valid = switch (current) {
            case PLACED -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PROCESSING || next == OrderStatus.CANCELLED;
            case PROCESSING -> next == OrderStatus.SHIPPED;
            case SHIPPED -> next == OrderStatus.DELIVERED;
            case DELIVERED -> next == OrderStatus.RETURNED;
            case CANCELLED, RETURNED -> false;
        };
        if (!valid) throw new BadRequestException("Invalid order status transition: " + current + " to " + next);
    }

    private BigDecimal settlePaymentsForCancellation(Order order) {
        BigDecimal refunded = BigDecimal.ZERO;
        for (Payment paymentView : paymentRepository.findByOrderOrderIdOrderByCreatedAtDesc(order.getOrderId())) {
            Payment payment = paymentRepository.findByIdForUpdate(paymentView.getPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found during cancellation"));
            if (payment.getPaymentStatus() == PaymentStatus.PENDING
                    || payment.getPaymentStatus() == PaymentStatus.AUTHORIZED) {
                payment.setPaymentStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
                continue;
            }
            if (payment.getPaymentStatus() != PaymentStatus.PAID
                    && payment.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) continue;

            BigDecimal alreadyRefunded = refundRepository.sumRefundedByPaymentId(payment.getPaymentId());
            if (alreadyRefunded == null) alreadyRefunded = BigDecimal.ZERO;
            BigDecimal remaining = payment.getAmount().subtract(alreadyRefunded).max(BigDecimal.ZERO);
            if (remaining.signum() == 0) {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
                continue;
            }
            Refund refund = new Refund();
            refund.setPayment(payment);
            refund.setRefundAmount(remaining);
            refund.setRefundStatus(RefundStatus.COMPLETED);
            refund.setReason("Order cancelled before fulfilment");
            refund.setProviderReference("CANCEL-" + order.getOrderId() + "-" + payment.getPaymentId());
            refund.setProcessedAt(LocalDateTime.now());
            refundRepository.save(refund);
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            refunded = refunded.add(remaining);
        }
        return refunded;
    }
}
