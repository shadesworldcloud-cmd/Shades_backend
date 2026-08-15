package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateRefundRequest;
import com.sunglassstore.entity.*;
import com.sunglassstore.entity.enums.PaymentStatus;
import com.sunglassstore.entity.enums.ReturnStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.repository.PaymentRepository;
import com.sunglassstore.repository.RefundRepository;
import com.sunglassstore.repository.ReturnRequestRepository;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.service.impl.RefundServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.sunglassstore.email.event.RefundCompletedEmailRequested;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefundServiceImplTest {
    private RefundRepository refunds;
    private PaymentRepository payments;
    private ReturnRequestRepository returns;
    private OrderRepository orders;
    private RefundServiceImpl service;
    private ApplicationEventPublisher events;

    @BeforeEach
    void setUp() {
        refunds = mock(RefundRepository.class); payments = mock(PaymentRepository.class);
        returns = mock(ReturnRequestRepository.class); orders = mock(OrderRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new RefundServiceImpl(refunds, payments, returns, orders, events);
    }

    @Test
    void refundCannotExceedSpecificReturnedMerchandiseValue() {
        Fixture fixture = fixture(new BigDecimal("5000.00"), new BigDecimal("1000.00"), 2, 1);
        stub(fixture);
        when(refunds.sumRefundedByPaymentId(3L)).thenReturn(BigDecimal.ZERO);
        when(refunds.sumRefundedByReturnId(4L)).thenReturn(BigDecimal.ZERO);
        when(refunds.sumRefundedByOrderId(7L)).thenReturn(BigDecimal.ZERO);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.processRefund(3L, request("501.00")));

        assertTrue(error.getMessage().contains("500.00"));
        verify(refunds, never()).save(any());
    }

    @Test
    void refundSubtractsEarlierRefundsForSameReturn() {
        Fixture fixture = fixture(new BigDecimal("5000.00"), new BigDecimal("1000.00"), 2, 1);
        stub(fixture);
        when(refunds.sumRefundedByPaymentId(3L)).thenReturn(new BigDecimal("200.00"));
        when(refunds.sumRefundedByReturnId(4L)).thenReturn(new BigDecimal("200.00"));
        when(refunds.sumRefundedByOrderId(7L)).thenReturn(new BigDecimal("200.00"));

        assertThrows(BadRequestException.class, () -> service.processRefund(3L, request("301.00")));
    }

    @Test
    void validRefundUsesLockedPaymentAndUpdatesStatus() {
        Fixture fixture = fixture(new BigDecimal("5000.00"), new BigDecimal("1000.00"), 2, 1);
        stub(fixture);
        when(refunds.sumRefundedByPaymentId(3L)).thenReturn(BigDecimal.ZERO);
        when(refunds.sumRefundedByReturnId(4L)).thenReturn(BigDecimal.ZERO);
        when(refunds.sumRefundedByOrderId(7L)).thenReturn(BigDecimal.ZERO);
        when(refunds.save(any(Refund.class))).thenAnswer(invocation -> { Refund value = invocation.getArgument(0); value.setRefundId(9L); return value; });

        var response = service.processRefund(3L, request("500.00"));

        assertEquals(new BigDecimal("500.00"), response.refundAmount());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, fixture.payment.getPaymentStatus());
        verify(payments).findByIdForUpdate(3L);
        verify(payments).save(fixture.payment);
        verify(events).publishEvent((Object) argThat(event -> event instanceof RefundCompletedEmailRequested email
                && email.refundId().equals(9L)
                && email.orderId().equals(7L)
                && email.amount().compareTo(new BigDecimal("500.00")) == 0));
    }

    @Test
    void refundRejectsReturnFromAnotherOrder() {
        Fixture fixture = fixture(new BigDecimal("5000.00"), new BigDecimal("1000.00"), 2, 1);
        Order anotherOrder = new Order(); anotherOrder.setOrderId(99L); fixture.returnRequest.setOrder(anotherOrder);
        stub(fixture);
        assertThrows(BadRequestException.class, () -> service.processRefund(3L, request("100.00")));
    }

    @Test
    void refundProratesOrderDiscountAndTaxAcrossReturnedItems() {
        Fixture fixture = fixture(new BigDecimal("525.00"), new BigDecimal("1000.00"), 2, 1);
        fixture.returnRequest.getOrder().setTotalAmount(new BigDecimal("525.00"));
        stub(fixture);
        when(refunds.sumRefundedByPaymentId(3L)).thenReturn(BigDecimal.ZERO);
        when(refunds.sumRefundedByReturnId(4L)).thenReturn(BigDecimal.ZERO);
        when(refunds.sumRefundedByOrderId(7L)).thenReturn(BigDecimal.ZERO);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.processRefund(3L, request("262.51")));

        assertTrue(error.getMessage().contains("262.50"));
    }

    @Test
    void refundCannotConsumeShippingAcrossSeparateReturns() {
        Fixture fixture = fixture(new BigDecimal("15.01"), new BigDecimal("10.01"), 3, 1);
        fixture.returnRequest.getOrder().setTotalAmount(new BigDecimal("15.01"));
        fixture.returnRequest.getOrder().setShippingAmount(new BigDecimal("5.00"));
        stub(fixture);
        when(refunds.sumRefundedByPaymentId(3L)).thenReturn(new BigDecimal("10.00"));
        when(refunds.sumRefundedByReturnId(4L)).thenReturn(BigDecimal.ZERO);
        when(refunds.sumRefundedByOrderId(7L)).thenReturn(new BigDecimal("10.00"));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.processRefund(3L, request("0.02")));

        assertTrue(error.getMessage().contains("0.01"));
    }

    @Test
    void negativeRefundIsRejectedBeforeDatabaseOrEmail() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.processRefund(3L, request("-1.00")));

        assertEquals("Refund amount must be positive", error.getMessage());
        verifyNoInteractions(payments, returns, orders, refunds, events);
    }

    @Test
    void overPrecisionRefundIsRejectedBeforeDatabaseOrEmail() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.processRefund(3L, request("1.001")));

        assertEquals("Refund amount must have at most 2 decimal places", error.getMessage());
        verifyNoInteractions(payments, returns, orders, refunds, events);
    }

    private void stub(Fixture fixture) {
        when(payments.findByIdForUpdate(3L)).thenReturn(Optional.of(fixture.payment));
        when(returns.findByIdForUpdate(4L)).thenReturn(Optional.of(fixture.returnRequest));
        when(orders.findByIdForUpdate(fixture.returnRequest.getOrder().getOrderId()))
                .thenReturn(Optional.of(fixture.returnRequest.getOrder()));
    }

    private CreateRefundRequest request(String amount) {
        CreateRefundRequest request = new CreateRefundRequest(); request.setReturnId(4L);
        request.setRefundAmount(new BigDecimal(amount)); request.setReason("Returned merchandise"); return request;
    }

    private Fixture fixture(BigDecimal paymentAmount, BigDecimal lineTotal, int purchased, int returned) {
        User user = new User(); user.setUserId(9L); user.setEmail("customer@example.com"); user.setName("Customer");
        Order order = new Order(); order.setOrderId(7L); order.setUser(user);
        order.setSubtotalAmount(lineTotal); order.setTotalAmount(lineTotal); order.setShippingAmount(BigDecimal.ZERO);
        Payment payment = new Payment(); payment.setPaymentId(3L); payment.setOrder(order);
        payment.setAmount(paymentAmount); payment.setPaymentStatus(PaymentStatus.PAID);
        OrderItem orderItem = new OrderItem(); orderItem.setOrder(order); orderItem.setQuantity(purchased); orderItem.setLineTotal(lineTotal);
        ReturnRequest returnRequest = new ReturnRequest(); returnRequest.setReturnId(4L); returnRequest.setOrder(order); returnRequest.setUser(user);
        returnRequest.setReturnStatus(ReturnStatus.RECEIVED);
        ReturnItem returnItem = new ReturnItem(); returnItem.setReturnRequest(returnRequest); returnItem.setOrderItem(orderItem); returnItem.setQuantity(returned);
        returnRequest.setItems(List.of(returnItem));
        return new Fixture(payment, returnRequest);
    }

    private record Fixture(Payment payment, ReturnRequest returnRequest) {}
}
