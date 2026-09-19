package com.sunglassstore.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Returned to the frontend so it can build the PayU redirect form.
 * Every field maps to a PayU _payment POST parameter.
 */
@Getter
@Builder
public class PayUInitiateResponse {
    private String action;       // PayU _payment URL
    private String key;
    private String txnid;
    private String amount;
    private String productinfo;
    private String firstname;
    private String email;
    private String phone;
    private String surl;
    private String furl;
    private String hash;
}
