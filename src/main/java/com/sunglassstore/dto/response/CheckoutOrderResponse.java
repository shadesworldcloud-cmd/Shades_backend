package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Order;

import java.math.BigDecimal;

public record CheckoutOrderResponse(Long orderId, String status, BigDecimal subtotalAmount,
                                    BigDecimal discountAmount, BigDecimal taxAmount,
                                    BigDecimal shippingAmount, BigDecimal totalAmount) {
    public static CheckoutOrderResponse fromEntity(Order order) {
        return new CheckoutOrderResponse(order.getOrderId(), order.getOrderStatus().name(),
                order.getSubtotalAmount(), order.getDiscountAmount(), order.getTaxAmount(),
                order.getShippingAmount(), order.getTotalAmount());
    }
}
