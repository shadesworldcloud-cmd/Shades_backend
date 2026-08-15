package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateReviewRequest;
import com.sunglassstore.dto.request.UpdateReviewRequest;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.OrderItem;
import com.sunglassstore.entity.Product;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.Review;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.ReviewStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.repository.OrderItemRepository;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.repository.ReviewRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.repository.ReturnItemRepository;
import com.sunglassstore.repository.RefundRepository;
import com.sunglassstore.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewServiceImplTest {
    private ReviewRepository reviews;
    private ProductRepository products;
    private UserRepository users;
    private OrderItemRepository orderItems;
    private ReturnItemRepository returnItems;
    private RefundRepository refunds;
    private ReviewServiceImpl service;
    private NotificationService notificationService;
    private CommunicationPreferenceService communicationPreferences;

    @BeforeEach
    void setUp() {
        reviews = mock(ReviewRepository.class);
        products = mock(ProductRepository.class);
        users = mock(UserRepository.class);
        orderItems = mock(OrderItemRepository.class);
        returnItems = mock(ReturnItemRepository.class);
        refunds = mock(RefundRepository.class);
        notificationService = mock(NotificationService.class);
        communicationPreferences = mock(CommunicationPreferenceService.class);
        when(communicationPreferences.allowsInApp(anyLong(), any())).thenReturn(true);
        service = new ReviewServiceImpl(reviews, products, users, orderItems, returnItems, refunds, notificationService, communicationPreferences);
    }

    @Test
    void createsReviewForExactDeliveredVariantPurchase() {
        Fixture fixture = fixture(5L, 3L, OrderStatus.DELIVERED);
        CreateReviewRequest request = request(3L, 20L);
        when(orderItems.findById(20L)).thenReturn(Optional.of(fixture.item));
        when(users.findById(5L)).thenReturn(Optional.of(fixture.user));
        when(products.findById(3L)).thenReturn(Optional.of(fixture.product));
        when(reviews.save(any(Review.class))).thenAnswer(invocation -> {
            Review value = invocation.getArgument(0); value.setReviewId(99L); return value;
        });

        var result = service.createReview(5L, request);

        assertEquals(20L, result.orderItemId());
        assertEquals(10L, result.variantId());
        assertEquals("Ocean Blue", result.variantName());
        assertEquals("SW-BLUE", result.variantSku());
        // Eligibility is the gate, not a moderator: an eligible review is live on submission.
        assertEquals(ReviewStatus.PUBLISHED, result.reviewStatus());
        verify(reviews).save(argThat(review -> review.getOrderItem() == fixture.item));
    }

    @Test
    void editingALiveReviewKeepsItLive() {
        Fixture fixture = fixture(5L, 3L, OrderStatus.DELIVERED);
        Review review = review(fixture, ReviewStatus.APPROVED);
        UpdateReviewRequest request = new UpdateReviewRequest(); request.setRating(4); request.setReviewText(" Updated ");
        when(reviews.findByReviewIdAndUserUserId(40L, 5L)).thenReturn(Optional.of(review));
        when(reviews.save(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.updateReview(5L, 40L, request);

        assertEquals(ReviewStatus.PUBLISHED, result.reviewStatus());
        assertEquals("Updated", result.reviewText());
    }

    @Test
    void editingARejectedReviewGoesBackIntoTheQueueRatherThanStraightBackOnThePage() {
        // A takedown must not be undoable by retyping the text.
        Fixture fixture = fixture(5L, 3L, OrderStatus.DELIVERED);
        Review review = review(fixture, ReviewStatus.REJECTED);
        UpdateReviewRequest request = new UpdateReviewRequest(); request.setRating(4); request.setReviewText("Reworded");
        when(reviews.findByReviewIdAndUserUserId(40L, 5L)).thenReturn(Optional.of(review));
        when(reviews.save(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.updateReview(5L, 40L, request);

        assertEquals(ReviewStatus.PENDING, result.reviewStatus());
        assertFalse(result.reviewStatus().isPubliclyVisible());
    }

    @Test
    void theProductListingShowsPublishedAndApprovedButNeverPendingOrRejected() {
        assertTrue(ReviewStatus.PUBLISHED.isPubliclyVisible());
        assertTrue(ReviewStatus.APPROVED.isPubliclyVisible());
        assertFalse(ReviewStatus.PENDING.isPubliclyVisible());
        assertFalse(ReviewStatus.REJECTED.isPubliclyVisible());
    }
    @Test
    void moderationRejectsPendingAsAnAdminDecision() {
        assertThrows(BadRequestException.class, () -> service.updateReviewStatus(40L, ReviewStatus.PENDING));
        verify(reviews, never()).findById(any());
    }

    @Test
    void moderationCannotRepeatCurrentStatus() {
        Fixture fixture = fixture(5L, 3L, OrderStatus.DELIVERED);
        when(reviews.findById(40L)).thenReturn(Optional.of(review(fixture, ReviewStatus.APPROVED)));
        assertThrows(BadRequestException.class, () -> service.updateReviewStatus(40L, ReviewStatus.APPROVED));
        verify(reviews, never()).save(any());
    }

    @Test
    void moderatorCanApprovePendingReview() {
        Fixture fixture = fixture(5L, 3L, OrderStatus.DELIVERED);
        Review review = review(fixture, ReviewStatus.PENDING);
        when(reviews.findById(40L)).thenReturn(Optional.of(review));
        when(reviews.save(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.updateReviewStatus(40L, ReviewStatus.APPROVED);

        assertEquals(ReviewStatus.APPROVED, result.reviewStatus());
        verify(notificationService).createNotification(eq(5L), eq(com.sunglassstore.entity.enums.NotificationType.IN_APP),
                eq("Review approved · Product #3"), contains("visible to shoppers"));
    }

    @Test
    void moderationSearchUsesNormalizedSearchAndMapsAdminContext() {
        Fixture fixture = fixture(5L, 3L, OrderStatus.DELIVERED);
        fixture.user.setEmail("customer@example.com");
        Review review = review(fixture, ReviewStatus.PENDING);
        var pageable = PageRequest.of(0, 20);
        when(reviews.searchForModeration(ReviewStatus.PENDING, "blue", pageable))
                .thenReturn(new PageImpl<>(List.of(review), pageable, 1));

        var result = service.getReviewsForModeration(ReviewStatus.PENDING, " blue ", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Barcelona", result.getContent().getFirst().productName());
        assertEquals("customer@example.com", result.getContent().getFirst().customerEmail());
    }

    @Test
    void rejectsAnotherCustomersOrderItem() {
        Fixture fixture = fixture(8L, 3L, OrderStatus.DELIVERED);
        when(orderItems.findById(20L)).thenReturn(Optional.of(fixture.item));

        assertThrows(BadRequestException.class, () -> service.createReview(5L, request(3L, 20L)));
        verify(reviews, never()).save(any());
    }

    @Test
    void rejectsVariantBeforeDelivery() {
        Fixture fixture = fixture(5L, 3L, OrderStatus.SHIPPED);
        when(orderItems.findById(20L)).thenReturn(Optional.of(fixture.item));

        assertThrows(BadRequestException.class, () -> service.createReview(5L, request(3L, 20L)));
    }

    @Test
    void rejectsOrderItemBelongingToAnotherProduct() {
        Fixture fixture = fixture(5L, 4L, OrderStatus.DELIVERED);
        when(orderItems.findById(20L)).thenReturn(Optional.of(fixture.item));

        assertThrows(BadRequestException.class, () -> service.createReview(5L, request(3L, 20L)));
    }

    @Test
    void rejectsSecondReviewForSamePurchasedOrderItem() {
        Fixture fixture = fixture(5L, 3L, OrderStatus.DELIVERED);
        when(orderItems.findById(20L)).thenReturn(Optional.of(fixture.item));
        when(reviews.existsByUserUserIdAndOrderItemOrderItemId(5L, 20L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createReview(5L, request(3L, 20L)));
    }

    @Test
    void rejectsFullyReturnedVariant() {
        Fixture fixture = fixture(5L, 3L, OrderStatus.DELIVERED);
        when(orderItems.findById(20L)).thenReturn(Optional.of(fixture.item));
        when(returnItems.sumReturnedQuantityByOrderItemId(20L)).thenReturn(1);

        assertThrows(BadRequestException.class, () -> service.createReview(5L, request(3L, 20L)));
    }

    @Test
    void rejectsVariantFromRefundedOrder() {
        Fixture fixture = fixture(5L, 3L, OrderStatus.DELIVERED);
        when(orderItems.findById(20L)).thenReturn(Optional.of(fixture.item));
        when(refunds.hasActiveStandaloneRefundForOrder(30L)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.createReview(5L, request(3L, 20L)));
    }

    @Test
    void reviewableVariantsExcludeOrderItemsAlreadyReviewed() {
        Fixture available = fixture(5L, 3L, OrderStatus.DELIVERED);
        Fixture reviewed = fixture(5L, 3L, OrderStatus.DELIVERED);
        reviewed.item.setOrderItemId(21L);
        when(orderItems.findDeliveredByUserAndProduct(5L, 3L)).thenReturn(List.of(available.item, reviewed.item));
        when(reviews.existsByUserUserIdAndOrderItemOrderItemId(5L, 21L)).thenReturn(true);

        var result = service.getReviewableVariants(5L, 3L);

        assertEquals(1, result.size());
        assertEquals(20L, result.getFirst().orderItemId());
    }

    private CreateReviewRequest request(Long productId, Long orderItemId) {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setProductId(productId); request.setOrderItemId(orderItemId);
        request.setRating(5); request.setReviewText(" Excellent ");
        return request;
    }

    private Fixture fixture(Long ownerId, Long productId, OrderStatus status) {
        User user = new User(); user.setUserId(ownerId); user.setName("Customer");
        Product product = new Product(); product.setProductId(productId); product.setProductName("Barcelona");
        ProductVariant variant = new ProductVariant(); variant.setVariantId(10L); variant.setProduct(product);
        variant.setVariantName("Ocean Blue"); variant.setSku("SW-BLUE");
        Order order = new Order(); order.setOrderId(30L); order.setUser(user); order.setOrderStatus(status);
        OrderItem item = new OrderItem(); item.setOrderItemId(20L); item.setOrder(order); item.setVariant(variant); item.setQuantity(1);
        return new Fixture(user, product, item);
    }

    private Review review(Fixture fixture, ReviewStatus status) {
        Review review = new Review(); review.setReviewId(40L); review.setUser(fixture.user);
        review.setProduct(fixture.product); review.setOrderItem(fixture.item); review.setRating(5);
        review.setReviewText("Excellent"); review.setReviewStatus(status);
        review.setCreatedAt(java.time.LocalDateTime.now()); review.setUpdatedAt(java.time.LocalDateTime.now());
        return review;
    }

    private record Fixture(User user, Product product, OrderItem item) {}
}
