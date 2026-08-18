package com.nexusflow.order.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequestDTO(
        @NotNull(message = "Customer ID is required")
        UUID customerId,

        @NotEmpty(message = "Order must contain at least one item")
        @Valid
        List<OrderItemRequestDTO> items
) {}
