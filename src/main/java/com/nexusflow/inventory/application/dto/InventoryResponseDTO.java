package com.nexusflow.inventory.application.dto;

import com.nexusflow.inventory.domain.Inventory;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryResponseDTO(
        UUID id,
        String sku,
        Integer physicalQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        String warehouse,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static InventoryResponseDTO fromEntity(Inventory inventory) {
        return new InventoryResponseDTO(
                inventory.getId(),
                inventory.getSku(),
                inventory.getPhysicalQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity(),
                inventory.getWarehouse(),
                inventory.getVersion(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt()
        );
    }
}
