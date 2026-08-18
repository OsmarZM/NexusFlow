package com.nexusflow.order.application.dto;

import com.nexusflow.order.domain.Order;
import com.nexusflow.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        UUID customerId,
        String customerName,
        String customerEmail,
        BigDecimal totalAmount,
        OrderStatus status,
        List<OrderItemResponseDTO> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static OrderResponseDTO fromEntity(Order order) {
        List<OrderItemResponseDTO> itemDTOs = order.getItems() != null
                ? order.getItems().stream().map(OrderItemResponseDTO::fromEntity).toList()
                : List.of();

        return new OrderResponseDTO(
                order.getId(),
                order.getCustomer().getId(),
                order.getCustomer().getName(),
                order.getCustomer().getEmail(),
                order.getTotalAmount(),
                order.getStatus(),
                itemDTOs,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
