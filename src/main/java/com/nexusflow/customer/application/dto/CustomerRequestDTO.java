package com.nexusflow.customer.application.dto;

import com.nexusflow.customer.domain.CustomerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequestDTO(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Document is required")
        @Size(min = 5, max = 32, message = "Document must be between 5 and 32 characters")
        String document,

        CustomerStatus status
) {}
