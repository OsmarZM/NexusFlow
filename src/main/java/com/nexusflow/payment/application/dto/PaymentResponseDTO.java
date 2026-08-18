package com.nexusflow.payment.application.dto;

import com.nexusflow.payment.domain.Payment;
import com.nexusflow.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponseDTO(
        UUID id,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        PaymentStatus status,
        String transactionReference,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PaymentResponseDTO fromEntity(Payment payment) {
        return new PaymentResponseDTO(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getTransactionReference(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
