package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateReturnRequest;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.OrderItem;
import com.sunglassstore.entity.ReturnRequest;
import com.sunglassstore.entity.ReturnItem;
import com.sunglassstore.entity.ProductVariant;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.ReturnStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.*;
import com.sunglassstore.service.impl.ReturnServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.sunglassstore.email.event.ReturnStatusEmailRequested;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReturnServiceImplTest {
    private ReturnRequestRepository returns;
    private OrderRepository orders;
    private OrderItemRepository orderItems;
    private ReturnItemRepository returnItems;
    private PaymentRepository payments;
    private RefundRepository refunds;
    private ProductVariantRepository variants;
    private InventoryMovementRepository movements;
    private ReturnServiceImpl service;
    private ApplicationEventPublisher events;

    @BeforeEach
    void setUp() {
        returns = mock(ReturnRequestRepository.class);
        orders = mock(OrderRepository.class);
        orderItems = mock(OrderItemRepository.class);
        returnItems = mock(ReturnItemRepository.class);
        payments = mock(PaymentRepository.class);
        refunds = mock(RefundRepository.class);
        variants = mock(ProductVariantRepository.class);
        movements = mock(InventoryMovementRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new ReturnServiceImpl(returns, returnItems, orders, orderItems,
                variants, movements, payments, refunds, events);
    }

    @Test
    void createReturn_rejectsExpiredReturnWindow() {
        Order order = deliveredOrder(7L, 30L, LocalDateTime.now().minusDays(31));
        when(orders.findByOrderIdAndUserUserId(7L, 30L)).thenReturn(Optional.of(order));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createReturn(30L, request(7L, 99L, 1, false)));

        assertEquals("The 30-day return window has expired", error.getMessage());
        verify(returns, never()).save(any());
    }

    @Test
    void createReturn_rejectsDuplicateOrderItemLines() {
        Order order = deliveredOrder(7L, 30L, LocalDateTime.now().minusDays(2));
        OrderItem item = new OrderItem();
        item.setOrderItemId(99L); item.setOrder(order); item.setQuantity(2); item.setProductName("Barcelona");
        when(orders.findByOrderIdAndUserUserId(7L, 30L)).thenReturn(Optional.of(order));
        when(orderItems.findByIdForUpdate(99L)).thenReturn(Optional.of(item));
        when(returnItems.sumReturnedQuantityByOrderItemId(99L)).thenReturn(0);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createReturn(30L, request(7L, 99L, 1, true)));

        assertEquals("The same order item cannot be included more than once", error.getMessage());
        verify(returns, never()).save(any());
    }

    @Test
    void cancelReturn_onlyAllowsOwnerWhileRequested() {
        ReturnRequest request = persistedReturn(4L, 30L, ReturnStatus.APPROVED);
        when(returns.findByIdForUpdate(4L)).thenReturn(Optional.of(request));
        assertThrows(BadRequestException.class, () -> service.cancelReturn(30L, 4L));
        assertThrows(ResourceNotFoundException.class, () -> service.cancelReturn(99L, 4L));
        verify(returns, never()).save(any());
    }

    @Test
    void cancelReturn_cancelsPendingRequest() {
        ReturnRequest request = persistedReturn(4L, 30L, ReturnStatus.REQUESTED);
        when(returns.findByIdForUpdate(4L)).thenReturn(Optional.of(request));
        when(returns.save(any(ReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.findByOrderOrderIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        when(refunds.findByReturnRequestReturnIdOrderByCreatedAtDesc(4L)).thenReturn(List.of());

        assertEquals("CANCELLED", service.cancelReturn(30L, 4L).returnStatus());
        verify(events).publishEvent((Object) argThat(event -> event instanceof ReturnStatusEmailRequested email
                && email.returnId().equals(4L) && email.status().equals("CANCELLED")));
    }

    @Test
    void receiveReturn_doesNotRestockDamagedItem() {
        ReturnRequest request = persistedReturn(4L, 30L, ReturnStatus.PICKED_UP);
        OrderItem orderItem = new OrderItem(); orderItem.setOrderItemId(99L); orderItem.setOrder(request.getOrder());
        orderItem.setQuantity(1); orderItem.setProductName("Barcelona"); orderItem.setVariant(new ProductVariant());
        ReturnItem item = new ReturnItem(); item.setReturnItemId(8L); item.setReturnRequest(request);
        item.setOrderItem(orderItem); item.setQuantity(1); item.setItemCondition("DAMAGED");
        request.setItems(List.of(item));
        when(returns.findByIdForUpdate(4L)).thenReturn(Optional.of(request));
        when(returns.save(any(ReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payments.findByOrderOrderIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        when(refunds.findByReturnRequestReturnIdOrderByCreatedAtDesc(4L)).thenReturn(List.of());

        assertEquals("RECEIVED", service.updateReturnStatus(4L, ReturnStatus.RECEIVED, null,
                Map.of(8L, "DAMAGED")).returnStatus());
        verifyNoInteractions(variants, movements);
    }

    @Test
    void completeReturn_marksFullyReturnedOrderReturned() {
        ReturnRequest request = persistedReturn(4L, 30L, ReturnStatus.RECEIVED);
        OrderItem orderItem = new OrderItem(); orderItem.setOrderItemId(99L); orderItem.setOrder(request.getOrder());
        orderItem.setQuantity(2); orderItem.setProductName("Barcelona");
        request.getOrder().setItems(List.of(orderItem));
        ReturnItem item = new ReturnItem(); item.setReturnItemId(8L); item.setReturnRequest(request);
        item.setOrderItem(orderItem); item.setQuantity(2); item.setItemCondition("UNOPENED");
        request.setItems(List.of(item));
        when(returns.findByIdForUpdate(4L)).thenReturn(Optional.of(request));
        when(returns.save(any(ReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(returnItems.sumPhysicallyReturnedQuantityByOrderItemId(99L)).thenReturn(2);
        when(payments.findByOrderOrderIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        when(refunds.findByReturnRequestReturnIdOrderByCreatedAtDesc(4L)).thenReturn(List.of());

        service.updateReturnStatus(4L, ReturnStatus.COMPLETED, null, null);

        assertEquals(OrderStatus.RETURNED, request.getOrder().getOrderStatus());
        verify(orders).save(request.getOrder());
    }

    @Test
    void completeReturn_doesNotCountPendingQuantitiesAsPhysicallyReturned() {
        ReturnRequest request = persistedReturn(4L, 30L, ReturnStatus.RECEIVED);
        OrderItem orderItem = new OrderItem(); orderItem.setOrderItemId(99L); orderItem.setOrder(request.getOrder());
        orderItem.setQuantity(2); orderItem.setProductName("Barcelona");
        request.getOrder().setItems(List.of(orderItem));
        ReturnItem item = new ReturnItem(); item.setReturnItemId(8L); item.setReturnRequest(request);
        item.setOrderItem(orderItem); item.setQuantity(1); item.setItemCondition("UNOPENED"); request.setItems(List.of(item));
        when(returns.findByIdForUpdate(4L)).thenReturn(Optional.of(request));
        when(returns.save(any(ReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(returnItems.sumPhysicallyReturnedQuantityByOrderItemId(99L)).thenReturn(1);
        when(payments.findByOrderOrderIdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        when(refunds.findByReturnRequestReturnIdOrderByCreatedAtDesc(4L)).thenReturn(List.of());

        service.updateReturnStatus(4L, ReturnStatus.COMPLETED, null, null);

        assertEquals(OrderStatus.DELIVERED, request.getOrder().getOrderStatus());
        verify(orders, never()).save(any());
    }

    @Test
    void receiveReturn_requiresAdminInspectedCondition() {
        ReturnRequest request = persistedReturn(4L, 30L, ReturnStatus.PICKED_UP);
        OrderItem orderItem = new OrderItem(); orderItem.setOrderItemId(99L); orderItem.setOrder(request.getOrder());
        ReturnItem item = new ReturnItem(); item.setReturnItemId(8L); item.setReturnRequest(request);
        item.setOrderItem(orderItem); item.setQuantity(1); item.setItemCondition("UNOPENED"); request.setItems(List.of(item));
        when(returns.findByIdForUpdate(4L)).thenReturn(Optional.of(request));

        assertThrows(BadRequestException.class,
                () -> service.updateReturnStatus(4L, ReturnStatus.RECEIVED, null, null));
        verify(returns, never()).save(any());
    }

    @Test
    void createReturn_rejectsNonPositiveQuantityBeforePersistingOrEmailing() {
        Order order = deliveredOrder(7L, 30L, LocalDateTime.now().minusDays(2));
        when(orders.findByOrderIdAndUserUserId(7L, 30L)).thenReturn(Optional.of(order));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createReturn(30L, request(7L, 99L, 0, false)));

        assertEquals("Return quantity must be positive", error.getMessage());
        verify(returns, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void duplicateStatusUpdateIsRejectedWithoutInventoryOrEmailSideEffects() {
        ReturnRequest request = persistedReturn(4L, 30L, ReturnStatus.RECEIVED);
        when(returns.findByIdForUpdate(4L)).thenReturn(Optional.of(request));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.updateReturnStatus(4L, ReturnStatus.RECEIVED, null, Map.of()));

        assertEquals("Return is already in status RECEIVED", error.getMessage());
        verify(returns, never()).save(any());
        verifyNoInteractions(variants, movements, events);
    }

    private Order deliveredOrder(Long orderId, Long userId, LocalDateTime deliveredAt) {
        User user = new User(); user.setUserId(userId); user.setName("Customer"); user.setEmail("customer@example.com");
        Order order = new Order(); order.setOrderId(orderId); order.setUser(user);
        order.setOrderStatus(OrderStatus.DELIVERED); order.setDeliveredAt(deliveredAt);
        return order;
    }

    private ReturnRequest persistedReturn(Long returnId, Long userId, ReturnStatus status) {
        Order order = deliveredOrder(7L, userId, LocalDateTime.now().minusDays(1));
        ReturnRequest request = new ReturnRequest(); request.setReturnId(returnId); request.setOrder(order);
        request.setUser(order.getUser()); request.setReturnStatus(status); request.setReturnReason("Other");
        return request;
    }

    private CreateReturnRequest request(Long orderId, Long itemId, int quantity, boolean duplicate) {
        CreateReturnRequest.ReturnItemRequest item = new CreateReturnRequest.ReturnItemRequest();
        item.setOrderItemId(itemId); item.setQuantity(quantity); item.setItemCondition("UNOPENED");
        CreateReturnRequest request = new CreateReturnRequest(); request.setOrderId(orderId); request.setReturnReason("Other");
        request.setItems(duplicate ? List.of(item, item) : List.of(item));
        return request;
    }
}
