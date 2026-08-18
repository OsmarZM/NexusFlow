package com.nexusflow.inventory.application.dto;

import com.nexusflow.inventory.domain.InventoryReservation;
import com.nexusflow.inventory.domain.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockReservationResponseDTO(
        UUID reservationId,
        UUID orderId,
        String sku,
        Integer quantity,
        ReservationStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
    public static StockReservationResponseDTO fromEntity(InventoryReservation reservation) {
        return new StockReservationResponseDTO(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getSku(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt()
        );
    }
}
