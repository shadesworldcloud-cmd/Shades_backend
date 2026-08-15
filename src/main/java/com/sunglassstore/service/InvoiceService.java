package com.sunglassstore.service;

import com.sunglassstore.dto.response.AdminOrderResponse;

public interface InvoiceService {
    byte[] generate(AdminOrderResponse order);
}
