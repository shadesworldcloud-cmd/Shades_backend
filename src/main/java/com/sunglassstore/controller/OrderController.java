package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CreateOrderRequest;
import com.sunglassstore.dto.request.UpdateOrderStatusRequest;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.dto.response.AdminOrderResponse;
import com.sunglassstore.dto.response.CheckoutOrderResponse;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.OrderService;
import com.sunglassstore.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<CheckoutOrderResponse> createOrder(@AuthenticationPrincipal SecurityUser principal,
                                              @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CheckoutOrderResponse.fromEntity(orderService.createOrder(principal.getUserId(), request)));
    }

    @GetMapping
    public ResponseEntity<Page<AdminOrderResponse>> getMyOrders(@AuthenticationPrincipal SecurityUser principal,
                                                                 Pageable pageable) {
        return ResponseEntity.ok(orderService.getUserOrdersForCustomer(principal.getUserId(), pageable));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderResponse> getMyOrder(@AuthenticationPrincipal SecurityUser principal,
                                             @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getUserOrderForCustomer(principal.getUserId(), orderId));
    }

    // JSON is listed alongside PDF so that ERRORS can still be represented. With PDF alone, an
    // order that is not found — or not yours — made the service throw, and Spring then had no
    // acceptable representation for the JSON error body: HttpMediaTypeNotAcceptableException, which
    // surfaced as 500. Authorization was being enforced correctly the whole time; only the status
    // was wrong, and a 500 on an access decision is both misleading and noisy in the logs.
    // A successful response is unaffected — invoiceResponse() sets application/pdf explicitly.
    @GetMapping(value = "/{orderId}/invoice", produces = { MediaType.APPLICATION_PDF_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<byte[]> downloadMyInvoice(@AuthenticationPrincipal SecurityUser principal,
                                                     @PathVariable Long orderId) {
        byte[] invoice = invoiceService.generate(orderService.getUserOrderForCustomer(principal.getUserId(), orderId));
        return invoiceResponse(orderId, invoice);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CheckoutOrderResponse> cancelOrder(@AuthenticationPrincipal SecurityUser principal,
                                              @PathVariable Long orderId) {
        return ResponseEntity.ok(CheckoutOrderResponse.fromEntity(orderService.cancelOrder(principal.getUserId(), orderId)));
    }

    // Admin endpoints
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<Page<AdminOrderResponse>> getAllOrders(Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrdersForAdmin(pageable));
    }

    @GetMapping("/admin/{orderId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<AdminOrderResponse> getOrderById(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderForAdmin(orderId));
    }

    @GetMapping(value = "/admin/{orderId}/invoice", produces = { MediaType.APPLICATION_PDF_VALUE, MediaType.APPLICATION_JSON_VALUE })
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<byte[]> downloadAdminInvoice(@PathVariable Long orderId) {
        return invoiceResponse(orderId, invoiceService.generate(orderService.getOrderForAdmin(orderId)));
    }

    @PatchMapping("/admin/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<AdminOrderResponse> updateOrderStatus(@PathVariable Long orderId,
                                                    @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatusForAdmin(
                orderId, OrderStatus.valueOf(request.getStatus()), request.getNotes()));
    }


    private ResponseEntity<byte[]> invoiceResponse(Long orderId, byte[] invoice) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=shades-world-invoice-" + orderId + ".pdf")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(invoice.length)
                .body(invoice);
    }
}
