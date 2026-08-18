package com.nexusflow.unit.product;

import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.inventory.application.dto.InventoryResponseDTO;
import com.nexusflow.product.application.ProductService;
import com.nexusflow.product.application.dto.ProductRequestDTO;
import com.nexusflow.product.application.dto.ProductResponseDTO;
import com.nexusflow.product.domain.Product;
import com.nexusflow.product.domain.ProductRepository;
import com.nexusflow.product.domain.ProductStatus;
import com.nexusflow.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("Should create product and auto-initialize inventory")
    void shouldCreateProductAndInitializeInventory() {
        ProductRequestDTO request = new ProductRequestDTO(
                "GPU-RTX5070",
                "NVIDIA GeForce RTX 5070 12GB",
                "High performance GPU",
                BigDecimal.valueOf(3499.00),
                ProductStatus.ACTIVE,
                50
        );

        UUID productId = UUID.randomUUID();
        when(productRepository.existsBySku("GPU-RTX5070")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(productId);
            p.setCreatedAt(OffsetDateTime.now());
            p.setUpdatedAt(OffsetDateTime.now());
            return p;
        });

        ProductResponseDTO response = productService.createProduct(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(productId);
        assertThat(response.sku()).isEqualTo("GPU-RTX5070");
        verify(inventoryService, times(1)).initializeInventory("GPU-RTX5070", 50, "DEFAULT_WH");
    }

    @Test
    @DisplayName("Should throw BusinessException when SKU already exists")
    void shouldThrowExceptionWhenSkuExists() {
        ProductRequestDTO request = new ProductRequestDTO(
                "GPU-RTX5070", "RTX 5070", null, BigDecimal.valueOf(3000), ProductStatus.ACTIVE, 10
        );

        when(productRepository.existsBySku("GPU-RTX5070")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        verify(productRepository, never()).save(any(Product.class));
        verify(inventoryService, never()).initializeInventory(anyString(), anyInt(), anyString());
    }
}
