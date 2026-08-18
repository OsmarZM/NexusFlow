package com.nexusflow.inventory.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockUpdateDTO(
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity to add must be positive")
        Integer quantity,

        String warehouse
) {}
