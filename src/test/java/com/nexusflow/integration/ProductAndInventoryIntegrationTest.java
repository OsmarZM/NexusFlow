package com.nexusflow.integration;

import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.inventory.application.dto.InventoryResponseDTO;
import com.nexusflow.product.application.ProductService;
import com.nexusflow.product.application.dto.ProductRequestDTO;
import com.nexusflow.product.application.dto.ProductResponseDTO;
import com.nexusflow.product.domain.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductAndInventoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    @Test
    @DisplayName("E2E: Should create product and auto-initialize inventory record in real PostgreSQL")
    void shouldCreateProductAndInventory() {
        String sku = "CPU-RYZEN-9950X";
        ProductRequestDTO request = new ProductRequestDTO(
                sku,
                "AMD Ryzen 9 9950X",
                "Flagship 16-Core Processor",
                BigDecimal.valueOf(3999.00),
                ProductStatus.ACTIVE,
                25
        );

        ProductResponseDTO product = productService.createProduct(request);

        assertThat(product.id()).isNotNull();
        assertThat(product.sku()).isEqualTo(sku);

        InventoryResponseDTO inventory = inventoryService.getInventoryBySku(sku);
        assertThat(inventory.sku()).isEqualTo(sku);
        assertThat(inventory.physicalQuantity()).isEqualTo(25);
        assertThat(inventory.availableQuantity()).isEqualTo(25);
        assertThat(inventory.reservedQuantity()).isEqualTo(0);
    }
}
