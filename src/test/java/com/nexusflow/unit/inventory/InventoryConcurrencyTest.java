package com.nexusflow.unit.inventory;

import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.inventory.application.dto.StockReservationRequestDTO;
import com.nexusflow.inventory.application.dto.StockReservationResponseDTO;
import com.nexusflow.inventory.domain.Inventory;
import com.nexusflow.inventory.domain.InventoryRepository;
import com.nexusflow.inventory.domain.InventoryReservation;
import com.nexusflow.inventory.domain.InventoryReservationRepository;
import com.nexusflow.shared.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryConcurrencyTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("Should prevent overselling when 30 concurrent threads attempt to reserve 5 available units")
    void shouldPreventOversellingUnderHighConcurrency() throws InterruptedException {
        final String sku = "GPU-RTX5070-LIMITED";
        final int initialStock = 5;
        final int numberOfThreads = 30;

        Inventory inventory = Inventory.builder()
                .sku(sku)
                .physicalQuantity(initialStock)
                .reservedQuantity(0)
                .version(1L)
                .build();

        when(inventoryRepository.findBySkuWithPessimisticLock(sku)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(invocation -> {
            InventoryReservation r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> capturedExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    StockReservationResponseDTO response = inventoryService.reserveStock(
                            new StockReservationRequestDTO(UUID.randomUUID(), sku, 1, 15),
                            true
                    );
                    if (response != null) {
                        successCount.incrementAndGet();
                    }
                } catch (InsufficientStockException e) {
                    failureCount.incrementAndGet();
                    capturedExceptions.add(e);
                } catch (Exception e) {
                    capturedExceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Trigger simultaneous start
        startLatch.countDown();
        finishLatch.await();
        executorService.shutdown();

        // Assertions: exactly 5 succeed, 25 fail, available stock is 0 (never negative)
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failureCount.get()).isEqualTo(25);
        assertThat(inventory.getPhysicalQuantity()).isEqualTo(5);
        assertThat(inventory.getReservedQuantity()).isEqualTo(5);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(0);
    }
}
