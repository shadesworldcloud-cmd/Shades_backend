package com.sunglassstore.dto.response;

import com.sunglassstore.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReturnResponse(Long returnId, Long orderId, String customerName, String customerEmail,
        String returnStatus, String returnReason, String customerComments, String adminComments,
        LocalDateTime requestedAt, LocalDateTime approvedAt, LocalDateTime receivedAt,
        LocalDateTime completedAt, List<Item> items, List<PaymentInfo> payments, List<RefundInfo> refunds) {
    public static ReturnResponse fromEntity(ReturnRequest request, List<Payment> payments, List<Refund> refunds) {
        return new ReturnResponse(request.getReturnId(), request.getOrder().getOrderId(), request.getUser().getName(),
                request.getUser().getEmail(), request.getReturnStatus().name(), request.getReturnReason(),
                request.getCustomerComments(), request.getAdminComments(), request.getRequestedAt(),
                request.getApprovedAt(), request.getReceivedAt(), request.getCompletedAt(),
                request.getItems().stream().map(i -> new Item(i.getReturnItemId(), i.getOrderItem().getOrderItemId(),
                        i.getOrderItem().getProductName(), i.getOrderItem().getSku(), i.getQuantity(),
                        i.getOrderItem().getUnitPrice(), i.getItemCondition(), i.getReturnReason())).toList(),
                payments.stream().map(p -> new PaymentInfo(p.getPaymentId(), p.getAmount(), p.getPaymentStatus().name(), p.getPaymentMethod())).toList(),
                refunds.stream().map(r -> new RefundInfo(r.getRefundId(), r.getRefundAmount(), r.getRefundStatus().name(), r.getReason(), r.getCreatedAt(), r.getProcessedAt())).toList());
    }
    public record Item(Long returnItemId, Long orderItemId, String productName, String sku, Integer quantity,
                       BigDecimal unitPrice, String itemCondition, String returnReason) {}
    public record PaymentInfo(Long paymentId, BigDecimal amount, String status, String method) {}
    public record RefundInfo(Long refundId, BigDecimal amount, String status, String reason,
                             LocalDateTime createdAt, LocalDateTime processedAt) {}
}
