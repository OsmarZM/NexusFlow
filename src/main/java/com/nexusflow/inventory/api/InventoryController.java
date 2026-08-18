package com.nexusflow.inventory.api;

import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.inventory.application.dto.InventoryResponseDTO;
import com.nexusflow.inventory.application.dto.StockReservationRequestDTO;
import com.nexusflow.inventory.application.dto.StockReservationResponseDTO;
import com.nexusflow.inventory.application.dto.StockUpdateDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory and stock reservation management")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{sku}")
    @Operation(summary = "Get stock levels for a specific SKU")
    public ResponseEntity<InventoryResponseDTO> getInventoryBySku(@PathVariable String sku) {
        return ResponseEntity.ok(inventoryService.getInventoryBySku(sku));
    }

    @PostMapping("/{sku}/replenish")
    @Operation(summary = "Replenish physical stock for a SKU")
    public ResponseEntity<InventoryResponseDTO> replenishStock(
            @PathVariable String sku,
            @Valid @RequestBody StockUpdateDTO request) {
        return ResponseEntity.ok(inventoryService.addStock(sku, request));
    }

    @PostMapping("/reservations")
    @Operation(summary = "Reserve stock for an order (supports optimistic or pessimistic lock)")
    public ResponseEntity<StockReservationResponseDTO> reserveStock(
            @Valid @RequestBody StockReservationRequestDTO request,
            @RequestParam(defaultValue = "true") boolean pessimisticLock) {
        StockReservationResponseDTO response = inventoryService.reserveStock(request, pessimisticLock);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/reservations/orders/{orderId}/skus/{sku}")
    @Operation(summary = "Release reserved stock (compensation)")
    public ResponseEntity<Void> releaseReservation(
            @PathVariable UUID orderId,
            @PathVariable String sku) {
        inventoryService.releaseReservation(orderId, sku);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reservations/orders/{orderId}/skus/{sku}/confirm")
    @Operation(summary = "Confirm stock deduction after successful payment")
    public ResponseEntity<Void> confirmReservation(
            @PathVariable UUID orderId,
            @PathVariable String sku) {
        inventoryService.confirmReservation(orderId, sku);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reservations/orders/{orderId}")
    @Operation(summary = "List reservations for an order")
    public ResponseEntity<List<StockReservationResponseDTO>> getReservationsForOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(inventoryService.getReservationsForOrder(orderId));
    }
}
