package com.nexusflow.customer.application.dto;

import com.nexusflow.customer.domain.Customer;
import com.nexusflow.customer.domain.CustomerStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponseDTO(
        UUID id,
        String name,
        String email,
        String document,
        CustomerStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CustomerResponseDTO fromEntity(Customer customer) {
        return new CustomerResponseDTO(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getDocument(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
