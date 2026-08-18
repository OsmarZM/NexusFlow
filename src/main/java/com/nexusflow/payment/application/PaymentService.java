package com.nexusflow.payment.application;

import com.nexusflow.messaging.event.PaymentProcessedEvent;
import com.nexusflow.order.domain.Order;
import com.nexusflow.order.domain.OrderRepository;
import com.nexusflow.outbox.application.OutboxService;
import com.nexusflow.payment.application.dto.PaymentRequestDTO;
import com.nexusflow.payment.application.dto.PaymentResponseDTO;
import com.nexusflow.payment.domain.Payment;
import com.nexusflow.payment.domain.PaymentRepository;
import com.nexusflow.payment.domain.PaymentStatus;
import com.nexusflow.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OutboxService outboxService;

    @Value("${nexusflow.kafka.topics.payment-processed:payments.processed}")
    private String paymentProcessedTopic;

    @Transactional
    public PaymentResponseDTO processPayment(PaymentRequestDTO request, String correlationId) {
        log.info("Processing payment for Order: {}, Amount: {}, SimulateFailure: {}",
                request.orderId(), request.amount(), request.simulateFailure());

        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + request.orderId()));

        boolean shouldFail = Boolean.TRUE.equals(request.simulateFailure());
        PaymentStatus status = shouldFail ? PaymentStatus.REJECTED : PaymentStatus.APPROVED;
        String failureReason = shouldFail ? "Payment gateway declined: Insufficient credit line" : null;
        String txRef = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .order(order)
                .customerId(request.customerId())
                .amount(request.amount())
                .status(status)
                .transactionReference(txRef)
                .failureReason(failureReason)
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment record created with ID: {} and status: {}", saved.getId(), saved.getStatus());

        // Emit domain event via Transactional Outbox
        PaymentProcessedEvent event = shouldFail
                ? PaymentProcessedEvent.failure(order.getId(), request.amount(), failureReason, correlationId)
                : PaymentProcessedEvent.success(saved.getId(), order.getId(), request.amount(), correlationId);

        outboxService.saveEvent("PAYMENT", saved.getId(), paymentProcessedTopic, event);

        return PaymentResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> getPaymentsForOrder(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .stream()
                .map(PaymentResponseDTO::fromEntity)
                .toList();
    }
}
