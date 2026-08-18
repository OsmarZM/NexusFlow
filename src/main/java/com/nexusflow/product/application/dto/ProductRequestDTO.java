package com.nexusflow.product.application.dto;

import com.nexusflow.product.domain.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequestDTO(
        @NotBlank(message = "SKU is required")
        @Size(min = 3, max = 64, message = "SKU must be between 3 and 64 characters")
        String sku,

        @NotBlank(message = "Product name is required")
        @Size(min = 2, max = 255, message = "Product name must be between 2 and 255 characters")
        String name,

        String description,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be greater than zero")
        BigDecimal price,

        ProductStatus status,

        Integer initialStock
) {}
