package com.nexusflow.unit.inventory;

import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.inventory.application.dto.StockReservationRequestDTO;
import com.nexusflow.inventory.application.dto.StockReservationResponseDTO;
import com.nexusflow.inventory.domain.*;
import com.nexusflow.shared.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("Should reserve stock successfully when available quantity is sufficient")
    void shouldReserveStockSuccessfully() {
        String sku = "GPU-RTX5070";
        UUID orderId = UUID.randomUUID();

        Inventory inventory = Inventory.builder()
                .sku(sku)
                .physicalQuantity(10)
                .reservedQuantity(2)
                .version(1L)
                .build();

        when(inventoryRepository.findBySkuWithPessimisticLock(sku)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(invocation -> {
            InventoryReservation r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        StockReservationRequestDTO request = new StockReservationRequestDTO(orderId, sku, 3, 15);
        StockReservationResponseDTO response = inventoryService.reserveStock(request, true);

        assertThat(response).isNotNull();
        assertThat(response.quantity()).isEqualTo(3);
        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(inventory.getReservedQuantity()).isEqualTo(5);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
        verify(inventoryRepository, times(1)).save(inventory);
    }

    @Test
    @DisplayName("Should throw InsufficientStockException when available quantity is insufficient")
    void shouldThrowExceptionWhenStockInsufficient() {
        String sku = "GPU-RTX5070";
        UUID orderId = UUID.randomUUID();

        Inventory inventory = Inventory.builder()
                .sku(sku)
                .physicalQuantity(10)
                .reservedQuantity(9) // Only 1 available
                .version(1L)
                .build();

        when(inventoryRepository.findBySkuWithPessimisticLock(sku)).thenReturn(Optional.of(inventory));

        StockReservationRequestDTO request = new StockReservationRequestDTO(orderId, sku, 2, 15);

        assertThatThrownBy(() -> inventoryService.reserveStock(request, true))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock for SKU 'GPU-RTX5070'");

        assertThat(inventory.getReservedQuantity()).isEqualTo(9);
        verify(inventoryRepository, never()).save(any(Inventory.class));
        verify(reservationRepository, never()).save(any(InventoryReservation.class));
    }
}
