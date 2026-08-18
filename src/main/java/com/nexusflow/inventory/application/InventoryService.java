package com.nexusflow.inventory.application;

import com.nexusflow.inventory.application.dto.InventoryResponseDTO;
import com.nexusflow.inventory.application.dto.StockReservationRequestDTO;
import com.nexusflow.inventory.application.dto.StockReservationResponseDTO;
import com.nexusflow.inventory.application.dto.StockUpdateDTO;
import com.nexusflow.inventory.domain.*;
import com.nexusflow.shared.exception.BusinessException;
import com.nexusflow.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public InventoryResponseDTO getInventoryBySku(String sku) {
        return inventoryRepository.findBySku(sku)
                .map(InventoryResponseDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for SKU: " + sku));
    }

    @Transactional
    public InventoryResponseDTO initializeInventory(String sku, int initialPhysicalStock, String warehouse) {
        log.info("Initializing inventory for SKU: {} with physical qty: {}", sku, initialPhysicalStock);

        if (inventoryRepository.existsBySku(sku)) {
            throw new BusinessException("Inventory already exists for SKU: " + sku);
        }

        Inventory inventory = Inventory.builder()
                .sku(sku)
                .physicalQuantity(Math.max(0, initialPhysicalStock))
                .reservedQuantity(0)
                .warehouse(warehouse != null ? warehouse : "DEFAULT_WH")
                .build();

        Inventory saved = inventoryRepository.save(inventory);
        return InventoryResponseDTO.fromEntity(saved);
    }

    @Transactional
    public InventoryResponseDTO addStock(String sku, StockUpdateDTO request) {
        log.info("Replenishing stock for SKU: {} by quantity: {}", sku, request.quantity());

        Inventory inventory = inventoryRepository.findBySkuWithPessimisticLock(sku)
                .orElseGet(() -> Inventory.builder()
                        .sku(sku)
                        .physicalQuantity(0)
                        .reservedQuantity(0)
                        .warehouse(request.warehouse() != null ? request.warehouse() : "DEFAULT_WH")
                        .build());

        inventory.addPhysicalStock(request.quantity());
        if (request.warehouse() != null && !request.warehouse().isBlank()) {
            inventory.setWarehouse(request.warehouse());
        }

        Inventory saved = inventoryRepository.save(inventory);
        return InventoryResponseDTO.fromEntity(saved);
    }

    @Transactional
    public StockReservationResponseDTO reserveStock(StockReservationRequestDTO request, boolean usePessimisticLock) {
        log.info("Processing stock reservation for Order: {}, SKU: {}, Qty: {}, PessimisticLock: {}",
                request.orderId(), request.sku(), request.quantity(), usePessimisticLock);

        Inventory inventory = usePessimisticLock
                ? inventoryRepository.findBySkuWithPessimisticLock(request.sku())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for SKU: " + request.sku()))
                : inventoryRepository.findBySku(request.sku())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for SKU: " + request.sku()));

        // Invariant check & stock reservation
        inventory.reserveStock(request.quantity());
        inventoryRepository.save(inventory);

        int ttl = request.ttlMinutes() != null ? request.ttlMinutes() : 15;
        InventoryReservation reservation = InventoryReservation.builder()
                .orderId(request.orderId())
                .sku(request.sku())
                .quantity(request.quantity())
                .status(ReservationStatus.RESERVED)
                .expiresAt(OffsetDateTime.now().plusMinutes(ttl))
                .build();

        InventoryReservation savedReservation = reservationRepository.save(reservation);
        log.info("Stock successfully reserved. Reservation ID: {}", savedReservation.getId());
        return StockReservationResponseDTO.fromEntity(savedReservation);
    }

    @Transactional
    public void releaseReservation(UUID orderId, String sku) {
        log.info("Releasing stock reservation for Order: {}, SKU: {}", orderId, sku);

        InventoryReservation reservation = reservationRepository.findByOrderIdAndSku(orderId, sku)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found for Order: " + orderId + " and SKU: " + sku));

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            log.warn("Reservation for Order: {} and SKU: {} is already cancelled", orderId, sku);
            return;
        }

        Inventory inventory = inventoryRepository.findBySkuWithPessimisticLock(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for SKU: " + sku));

        inventory.releaseStock(reservation.getQuantity());
        inventoryRepository.save(inventory);

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        log.info("Reservation successfully cancelled and stock released.");
    }

    @Transactional
    public void confirmReservation(UUID orderId, String sku) {
        log.info("Confirming stock deduction for Order: {}, SKU: {}", orderId, sku);

        InventoryReservation reservation = reservationRepository.findByOrderIdAndSku(orderId, sku)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found for Order: " + orderId + " and SKU: " + sku));

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return;
        }

        Inventory inventory = inventoryRepository.findBySkuWithPessimisticLock(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for SKU: " + sku));

        inventory.deductStock(reservation.getQuantity());
        inventoryRepository.save(inventory);

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);
        log.info("Reservation confirmed and physical stock deducted.");
    }

    @Transactional(readOnly = true)
    public List<StockReservationResponseDTO> getReservationsForOrder(UUID orderId) {
        return reservationRepository.findByOrderId(orderId)
                .stream()
                .map(StockReservationResponseDTO::fromEntity)
                .toList();
    }
}
