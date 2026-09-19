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
     */
    public String generatePaymentHash(String txnid, String amount, String productinfo,
                                       String firstname, String email) {
        // key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||salt
        String hashString = payUConfig.getMerchantKey() + "|"
                + txnid + "|"
                + amount + "|"
                + productinfo + "|"
                + firstname + "|"
                + email + "|"
                + "||||||||||||"
                + payUConfig.getMerchantSalt();
        return DigestUtils.sha512Hex(hashString);
    }

    /**
     * Verify the response hash sent back by PayU (reverse hash).
     */
    public boolean verifyResponseHash(String responseHash, String txnid, String amount,
                                       String productinfo, String firstname, String email,
                                       String status) {
        // salt|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key
        String hashString = payUConfig.getMerchantSalt() + "|"
                + status + "|"
                + "|||||||||||"
                + email + "|"
                + firstname + "|"
                + productinfo + "|"
                + amount + "|"
                + txnid + "|"
                + payUConfig.getMerchantKey();
        return DigestUtils.sha512Hex(hashString).equals(responseHash);
    }
}
