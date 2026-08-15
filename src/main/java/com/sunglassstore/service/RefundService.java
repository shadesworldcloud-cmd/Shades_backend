package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateRefundRequest;
import com.sunglassstore.dto.response.RefundResponse;

public interface RefundService {
    RefundResponse processRefund(Long paymentId, CreateRefundRequest request);
}
