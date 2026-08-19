package com.nexusflow.inventory.application.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexusflow.inventory.domain.Inventory;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryResponseDTO(
        @JsonProperty("id") UUID id,
        @JsonProperty("sku") String sku,
        @JsonProperty("physicalQuantity") Integer physicalQuantity,
        @JsonProperty("reservedQuantity") Integer reservedQuantity,
        @JsonProperty("availableQuantity") Integer availableQuantity,
        @JsonProperty("warehouse") String warehouse,
        @JsonProperty("version") Long version,
        @JsonProperty("createdAt") OffsetDateTime createdAt,
        @JsonProperty("updatedAt") OffsetDateTime updatedAt
) implements Serializable {

    @JsonCreator
    public InventoryResponseDTO {
    }

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
