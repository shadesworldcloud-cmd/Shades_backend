package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Refund;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(Long refundId, Long paymentId, Long orderId, Long returnId,
        BigDecimal refundAmount, String refundStatus, String reason, LocalDateTime createdAt, LocalDateTime processedAt) {
    public static RefundResponse fromEntity(Refund refund) {
        return new RefundResponse(refund.getRefundId(), refund.getPayment().getPaymentId(),
                refund.getPayment().getOrder().getOrderId(), refund.getReturnRequest() == null ? null : refund.getReturnRequest().getReturnId(),
                refund.getRefundAmount(), refund.getRefundStatus().name(), refund.getReason(), refund.getCreatedAt(), refund.getProcessedAt());
    }
}
