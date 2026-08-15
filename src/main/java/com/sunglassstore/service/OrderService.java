package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateOrderRequest;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.dto.response.AdminOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    Order createOrder(Long userId, CreateOrderRequest request);
    Page<Order> getUserOrders(Long userId, Pageable pageable);
    Order getUserOrder(Long userId, Long orderId);
    Order cancelOrder(Long userId, Long orderId);

    /**
     * Cancels an order that was created but never paid for, releasing its reserved stock.
     * Returns false when the order is no longer eligible — already paid, already cancelled, or
     * gone — so the caller can distinguish "released" from "left alone" without catching.
     */
    boolean expireUnpaidOrder(Long orderId);
    Page<Order> getAllOrders(Pageable pageable);
    Order getOrderById(Long orderId);
    Order updateOrderStatus(Long orderId, OrderStatus status, String note);
    Page<AdminOrderResponse> getAllOrdersForAdmin(Pageable pageable);
    Page<AdminOrderResponse> getUserOrdersForCustomer(Long userId, Pageable pageable);
    AdminOrderResponse getOrderForAdmin(Long orderId);
    AdminOrderResponse getUserOrderForCustomer(Long userId, Long orderId);
    AdminOrderResponse updateOrderStatusForAdmin(Long orderId, OrderStatus status, String note);
}
