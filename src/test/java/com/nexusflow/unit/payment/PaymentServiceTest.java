package com.nexusflow.unit.payment;

import com.nexusflow.customer.domain.Customer;
import com.nexusflow.order.domain.Order;
import com.nexusflow.order.domain.OrderRepository;
import com.nexusflow.order.domain.OrderStatus;
import com.nexusflow.outbox.application.OutboxService;
import com.nexusflow.payment.application.PaymentService;
import com.nexusflow.payment.application.dto.PaymentRequestDTO;
import com.nexusflow.payment.application.dto.PaymentResponseDTO;
import com.nexusflow.payment.domain.Payment;
import com.nexusflow.payment.domain.PaymentRepository;
import com.nexusflow.payment.domain.PaymentStatus;
import com.nexusflow.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private PaymentService paymentService;

    private Order sampleOrder;
    private Customer sampleCustomer;
    private UUID orderId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "paymentProcessedTopic", "payments.processed");

        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        sampleCustomer = Customer.builder()
                .id(customerId)
                .name("Bruce Wayne")
                .email("bruce@waynecorp.com")
                .build();

        sampleOrder = Order.builder()
                .id(orderId)
                .customer(sampleCustomer)
                .status(OrderStatus.WAITING_PAYMENT)
                .totalAmount(BigDecimal.valueOf(5000.00))
                .build();
    }

    @Test
    @DisplayName("Should process payment successfully when customer and amount match order")
    void shouldProcessPaymentSuccessfully() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentRequestDTO request = new PaymentRequestDTO(orderId, customerId, BigDecimal.valueOf(5000.00), false);
        PaymentResponseDTO response = paymentService.processPayment(request, "corr-test-1");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));
        verify(outboxService, times(1)).saveEvent(eq("PAYMENT"), any(), eq("payments.processed"), any());
    }

    @Test
    @DisplayName("Anti-Tampering: Should reject payment when client sends fraudulent amount")
    void shouldRejectPaymentWhenAmountIsTampered() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));

        // Real order total is 5000, client attempts to pay 1.00
        PaymentRequestDTO request = new PaymentRequestDTO(orderId, customerId, BigDecimal.valueOf(1.00), false);

        assertThatThrownBy(() -> paymentService.processPayment(request, "corr-fraud-test"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Payment amount mismatch");

        verify(paymentRepository, never()).save(any());
        verify(outboxService, never()).saveEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Anti-Tampering: Should reject payment when client sends wrong customer ID")
    void shouldRejectPaymentWhenCustomerIdIsTampered() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));

        UUID wrongCustomerId = UUID.randomUUID();
        PaymentRequestDTO request = new PaymentRequestDTO(orderId, wrongCustomerId, BigDecimal.valueOf(5000.00), false);

        assertThatThrownBy(() -> paymentService.processPayment(request, "corr-fraud-test"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid customer for payment");

        verify(paymentRepository, never()).save(any());
        verify(outboxService, never()).saveEvent(any(), any(), any(), any());
    }
}
