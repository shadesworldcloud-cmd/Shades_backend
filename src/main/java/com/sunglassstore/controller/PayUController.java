package com.sunglassstore.controller;

import com.sunglassstore.config.PayUConfig;
import com.sunglassstore.dto.response.PayUInitiateResponse;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.Payment;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.PaymentStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.notification.event.OrderPaymentConfirmed;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.repository.PaymentRepository;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.OrderService;
import com.sunglassstore.service.PayUHashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the PayU payment lifecycle:
 *
 *   1. POST /api/payments/payu/initiate/{orderId}  — authenticated, returns form data for redirect
 *   2. POST /api/payments/payu/success              — PayU redirects browser here (surl)
 *   3. POST /api/payments/payu/failure              — PayU redirects browser here (furl)
 *   4. POST /api/payments/payu/webhook              — server-to-server confirmation (unauthenticated)
 *
 * surl / furl point to THIS controller (not the frontend) because PayU sends a
 * form POST — the browser needs a server that can accept it, verify the hash,
 * update the database, and only THEN redirect to the React frontend page.
 */
@RestController
@RequestMapping("/api/payments/payu")
@RequiredArgsConstructor
@Slf4j
public class PayUController {

    private final PayUConfig payUConfig;
    private final PayUHashService payUHashService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    // ── 1. Initiate: generate PayU form parameters ──────────────────────────

    @PostMapping("/initiate/{orderId}")
    public ResponseEntity<PayUInitiateResponse> initiatePayment(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable Long orderId) {

        Order order = orderRepository.findByOrderIdAndUserUserId(orderId, principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.PLACED) {
            throw new BadRequestException("Payment can only be initiated for orders in PLACED status");
        }

        // Idempotent: if already paid, reject
        Optional<Payment> existingPaid = paymentRepository
                .findFirstByOrderOrderIdAndPaymentStatus(orderId, PaymentStatus.PAID);
        if (existingPaid.isPresent()) {
            throw new BadRequestException("This order has already been paid");
        }

        String txnid = "SW-" + orderId + "-" + System.currentTimeMillis();
        String amount = order.getTotalAmount().toPlainString();
        String productinfo = "Shades World Order #" + orderId;
        String firstname = order.getUser().getName().split("\\s+")[0];
        String email = order.getUser().getEmail();
        String phone = order.getUser().getPhoneNumber() != null
                ? order.getUser().getPhoneNumber() : "";

        String hash = payUHashService.generatePaymentHash(
                txnid, amount, productinfo, firstname, email);

        // Create a PENDING payment record
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod("PAYU");
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentProvider("PAYU");
        payment.setProviderReference(txnid);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        PayUInitiateResponse response = PayUInitiateResponse.builder()
                .action(payUConfig.getBaseUrl() + "/_payment")
                .key(payUConfig.getMerchantKey())
                .txnid(txnid)
                .amount(amount)
                .productinfo(productinfo)
                .firstname(firstname)
                .email(email)
                .phone(phone)
                .surl(payUConfig.getSuccessUrl())
                .furl(payUConfig.getFailureUrl())
                .hash(hash)
                .build();

        return ResponseEntity.ok(response);
    }

    // ── 2. Success redirect (browser lands here via PayU surl) ──────────────
    //
    // PayU form-POSTs to this endpoint. We verify the hash, update the payment,
    // then 302-redirect the browser to the React frontend success page.

    @PostMapping("/success")
    @Transactional
    public ResponseEntity<Void> handleSuccess(@RequestParam Map<String, String> params) {
        log.info("PayU success callback received for txnid={}", params.get("txnid"));
        CallbackResult result = processPayUCallback(params);

        String redirectUrl = frontendBaseUrl + "/payment/success"
                + "?orderId=" + result.orderId
                + "&txnid=" + params.getOrDefault("txnid", "");

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    // ── 3. Failure redirect ─────────────────────────────────────────────────

    @PostMapping("/failure")
    @Transactional
    public ResponseEntity<Void> handleFailure(@RequestParam Map<String, String> params) {
        log.info("PayU failure callback received for txnid={}", params.get("txnid"));
        CallbackResult result = processPayUCallback(params);

        String redirectUrl = frontendBaseUrl + "/payment/failure"
                + "?orderId=" + result.orderId
                + "&reason=" + java.net.URLEncoder.encode(
                        result.message, java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    // ── 4. Webhook (server-to-server, unauthenticated) ──────────────────────

    @PostMapping("/webhook")
    @Transactional
    public ResponseEntity<String> handleWebhook(@RequestParam Map<String, String> params) {
        log.info("PayU webhook received for txnid={}", params.get("txnid"));
        processPayUCallback(params);
        return ResponseEntity.ok("OK");
    }

    // ── Internal DTO for callback results ──────────────────────────────────

    private record CallbackResult(boolean success, Long orderId, String message) {}

    // ── Shared callback processing ──────────────────────────────────────────

    private CallbackResult processPayUCallback(Map<String, String> params) {

        String txnid = params.getOrDefault("txnid", "");
        String amount = params.getOrDefault("amount", "");
        String productinfo = params.getOrDefault("productinfo", "");
        String firstname = params.getOrDefault("firstname", "");
        String email = params.getOrDefault("email", "");
        String status = params.getOrDefault("status", "");
        String hash = params.getOrDefault("hash", "");
        String mihpayid = params.getOrDefault("mihpayid", "");

        // Verify hash
        boolean hashValid = payUHashService.verifyResponseHash(
                hash, txnid, amount, productinfo, firstname, email, status);

        if (!hashValid) {
            log.error("PayU hash verification failed for txnid={}", txnid);
            return new CallbackResult(false, 0L, "Hash verification failed");
        }

        // Look up the pending payment by providerReference (txnid)
        Payment payment = paymentRepository.findByProviderReference(txnid)
                .orElse(null);

        if (payment == null) {
            log.warn("No payment found for txnid={}", txnid);
            return new CallbackResult(false, 0L, "Payment record not found");
        }

        Long orderId = payment.getOrder().getOrderId();

        // Already confirmed — idempotent
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            return new CallbackResult(true, orderId, "Payment already confirmed");
        }

        if ("success".equalsIgnoreCase(status)) {
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            payment.setProviderReference(mihpayid + "|" + txnid);
            paymentRepository.save(payment);

            Order order = payment.getOrder();
            orderService.updateOrderStatus(order.getOrderId(), OrderStatus.CONFIRMED, "PayU payment confirmed");
            eventPublisher.publishEvent(new OrderPaymentConfirmed(
                    order.getUser().getUserId(), order.getOrderId(),
                    order.getUser().getName(), order.getTotalAmount()));

            return new CallbackResult(true, orderId, "Payment confirmed");
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            return new CallbackResult(false, orderId,
                    "Payment failed: " + params.getOrDefault("error_Message", status));
        }
    }
}
