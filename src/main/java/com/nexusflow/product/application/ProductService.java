package com.nexusflow.product.application;

import com.nexusflow.inventory.application.InventoryService;
import com.nexusflow.product.application.dto.ProductRequestDTO;
import com.nexusflow.product.application.dto.ProductResponseDTO;
import com.nexusflow.product.domain.Product;
import com.nexusflow.product.domain.ProductRepository;
import com.nexusflow.product.domain.ProductStatus;
import com.nexusflow.shared.exception.BusinessException;
import com.nexusflow.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO request) {
        String sku = request.sku().trim().toUpperCase();
        log.info("Creating product with SKU: {}", sku);

        if (productRepository.existsBySku(sku)) {
            throw new BusinessException("A product with SKU '" + sku + "' already exists.");
        }

        Product product = Product.builder()
                .sku(sku)
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .price(request.price())
                .status(request.status() != null ? request.status() : ProductStatus.ACTIVE)
                .build();

        Product saved = productRepository.save(product);

        // Auto-initialize inventory record
        int initialStock = request.initialStock() != null ? request.initialStock() : 0;
        inventoryService.initializeInventory(sku, initialStock, "DEFAULT_WH");

        log.info("Product and inventory successfully created with ID: {}", saved.getId());
        return ProductResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(UUID id) {
        return productRepository.findById(id)
                .map(ProductResponseDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductBySku(String sku) {
        return productRepository.findBySku(sku.trim().toUpperCase())
                .map(ProductResponseDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with SKU: " + sku));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> listProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(ProductResponseDTO::fromEntity);
    }

    @Transactional
    public ProductResponseDTO updateProduct(UUID id, ProductRequestDTO request) {
        log.info("Updating product ID: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        String newSku = request.sku().trim().toUpperCase();
        if (!product.getSku().equalsIgnoreCase(newSku) && productRepository.existsBySku(newSku)) {
            throw new BusinessException("SKU '" + newSku + "' is already in use by another product.");
        }

        product.setSku(newSku);
        product.setName(request.name().trim());
        product.setDescription(request.description() != null ? request.description().trim() : null);
        product.setPrice(request.price());
        if (request.status() != null) {
            product.setStatus(request.status());
        }

        Product updated = productRepository.save(product);
        return ProductResponseDTO.fromEntity(updated);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        log.info("Deactivating product ID: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);
    }
}
