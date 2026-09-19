package com.sunglassstore.service;

import com.sunglassstore.config.PayUConfig;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

/**
 * Generates and verifies SHA-512 hashes for PayU payment requests and responses.
 *
 * Request hash:
 *   sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||salt)
 *
 * Response (reverse) hash:
 *   sha512(salt|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
 */
@Service
@RequiredArgsConstructor
public class PayUHashService {

    private final PayUConfig payUConfig;

    /**
     * Generate the hash for a payment initiation request.
     *
     * Formula (17 pipe-delimited fields):
     * key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||salt
     */
    public String generatePaymentHash(String txnid, String amount, String productinfo,
                                       String firstname, String email) {
        String hashString = String.join("|",
                payUConfig.getMerchantKey(),
                txnid, amount, productinfo, firstname, email,
                "", "", "", "", "",   // udf1–udf5 (unused)
                "", "", "", "", "",   // 5 reserved empty fields
                payUConfig.getMerchantSalt());
        return DigestUtils.sha512Hex(hashString);
    }

    /**
     * Verify the response hash sent back by PayU (reverse hash).
     *
     * Formula (18 pipe-delimited fields):
     * salt|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key
     */
    public boolean verifyResponseHash(String responseHash, String txnid, String amount,
                                       String productinfo, String firstname, String email,
                                       String status) {
        String hashString = String.join("|",
                payUConfig.getMerchantSalt(),
                status,
                "", "", "", "", "",   // 5 reserved empty fields
                "", "", "", "", "",   // udf5–udf1 (unused, reversed)
                email, firstname, productinfo, amount, txnid,
                payUConfig.getMerchantKey());
        return DigestUtils.sha512Hex(hashString).equals(responseHash);
    }
}
