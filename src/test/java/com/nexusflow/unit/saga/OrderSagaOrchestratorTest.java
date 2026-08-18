package com.nexusflow.unit.saga;

import com.nexusflow.idempotency.application.IdempotencyService;
import com.nexusflow.messaging.event.PaymentProcessedEvent;
import com.nexusflow.order.application.OrderService;
import com.nexusflow.saga.OrderSagaOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    private OrderService orderService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private OrderSagaOrchestrator sagaOrchestrator;

    @Test
    @DisplayName("Should confirm order when payment is approved")
    void shouldConfirmOrderOnPaymentApproved() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentProcessedEvent event = PaymentProcessedEvent.success(paymentId, orderId, BigDecimal.valueOf(2500.00), "corr-1");

        when(idempotencyService.isAlreadyProcessed(event.getEventId(), "OrderSagaOrchestrator")).thenReturn(false);

        sagaOrchestrator.handlePaymentProcessed(event, "corr-1");

        verify(orderService, times(1)).markAsPaid(orderId);
        verify(orderService, never()).cancelOrder(any(), any());
        verify(idempotencyService, times(1)).markAsProcessed(event.getEventId(), event.getEventType(), "OrderSagaOrchestrator");
    }

    @Test
    @DisplayName("Should trigger compensating transaction (cancel order and release stock) when payment fails")
    void shouldTriggerCompensatingTransactionOnPaymentFailure() {
        UUID orderId = UUID.randomUUID();
        PaymentProcessedEvent event = PaymentProcessedEvent.failure(orderId, BigDecimal.valueOf(2500.00), "Insufficient credit", "corr-2");

        when(idempotencyService.isAlreadyProcessed(event.getEventId(), "OrderSagaOrchestrator")).thenReturn(false);

        sagaOrchestrator.handlePaymentProcessed(event, "corr-2");

        verify(orderService, times(1)).cancelOrder(eq(orderId), contains("Insufficient credit"));
        verify(orderService, never()).markAsPaid(any());
        verify(idempotencyService, times(1)).markAsProcessed(event.getEventId(), event.getEventType(), "OrderSagaOrchestrator");
    }

    @Test
    @DisplayName("Should skip processing duplicate payment events (Idempotency)")
    void shouldSkipDuplicateEvent() {
        UUID orderId = UUID.randomUUID();
        PaymentProcessedEvent event = PaymentProcessedEvent.success(UUID.randomUUID(), orderId, BigDecimal.valueOf(100.00), "corr-3");

        when(idempotencyService.isAlreadyProcessed(event.getEventId(), "OrderSagaOrchestrator")).thenReturn(true);

        sagaOrchestrator.handlePaymentProcessed(event, "corr-3");

        verify(orderService, never()).markAsPaid(any());
        verify(orderService, never()).cancelOrder(any(), any());
        verify(idempotencyService, never()).markAsProcessed(any(), any(), any());
    }
}
