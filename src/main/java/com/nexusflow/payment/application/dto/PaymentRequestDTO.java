package com.nexusflow.payment.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestDTO(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        Boolean simulateFailure
) {}
