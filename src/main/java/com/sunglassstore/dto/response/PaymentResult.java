package com.sunglassstore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PaymentResult {

    private boolean success;
    private String providerReference;
    private String message;
}
