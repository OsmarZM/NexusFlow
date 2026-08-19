package com.nexusflow.integration;

import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.inventory.application.dto.StockReservationRequestDTO;
import com.nexusflow.inventory.domain.Inventory;
import com.nexusflow.inventory.domain.InventoryRepository;
import com.nexusflow.inventory.domain.InventoryReservationRepository;
import com.nexusflow.inventory.domain.ReservationStatus;
import com.nexusflow.product.application.ProductService;
import com.nexusflow.product.application.dto.ProductRequestDTO;
import com.nexusflow.product.domain.ProductStatus;
import com.nexusflow.shared.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryPessimisticLockIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Test
    @DisplayName("Real PostgreSQL Pessimistic Lock: 30 concurrent threads reserving 5 units should result in exactly 5 successes, 25 InsufficientStockException and 0 overselling")
    void shouldPreventOversellingUnderRealPostgresPessimisticLock() throws InterruptedException {
        final String sku = "GPU-RTX5090-LOCK-TEST";
        final int initialStock = 5;
        final int numberOfThreads = 30;

        // 1. Create Product & Inventory in real PostgreSQL 16
        productService.createProduct(new ProductRequestDTO(
                sku,
                "NVIDIA GeForce RTX 5090",
                "High-performance GPU for concurrency test",
                BigDecimal.valueOf(14999.00),
                ProductStatus.ACTIVE,
                initialStock
        ));

        // Verify initial state in DB
        Inventory initialInventory = inventoryRepository.findBySku(sku).orElseThrow();
        assertThat(initialInventory.getPhysicalQuantity()).isEqualTo(5);
        assertThat(initialInventory.getReservedQuantity()).isEqualTo(0);
        assertThat(initialInventory.getAvailableQuantity()).isEqualTo(5);

        // 2. Launch 30 concurrent threads trying to reserve 1 unit each simultaneously
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientStockCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startGate.await(); // Wait for all 30 threads to be ready
                    inventoryService.reserveStock(
                            new StockReservationRequestDTO(UUID.randomUUID(), sku, 1, 30),
                            true // usePessimisticLock = true (SELECT FOR UPDATE)
                    );
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    insufficientStockCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    finishGate.countDown();
                }
            });
        }

        // Fire all threads at the exact same instant
        startGate.countDown();
        boolean completed = finishGate.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // 3. Assertions proving 100% atomic correctness in PostgreSQL
        assertThat(completed).isTrue();
        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(insufficientStockCount.get()).isEqualTo(25);

        // 4. Assert direct PostgreSQL table state
        Inventory finalInventory = inventoryRepository.findBySku(sku).orElseThrow();
        assertThat(finalInventory.getPhysicalQuantity()).isEqualTo(5);
        assertThat(finalInventory.getReservedQuantity()).isEqualTo(5);
        assertThat(finalInventory.getAvailableQuantity()).isEqualTo(0);

        long activeReservations = reservationRepository.countBySkuAndStatus(sku, ReservationStatus.RESERVED);
        assertThat(activeReservations).isEqualTo(5);
    }
}
