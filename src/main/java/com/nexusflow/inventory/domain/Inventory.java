package com.nexusflow.inventory.domain;

import com.nexusflow.shared.exception.InsufficientStockException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "physical_quantity", nullable = false)
    @Builder.Default
    private Integer physicalQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String warehouse = "DEFAULT_WH";

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public int getAvailableQuantity() {
        return Math.max(0, this.physicalQuantity - this.reservedQuantity);
    }

    public synchronized void reserveStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }
        int available = getAvailableQuantity();
        if (available < quantity) {
            throw new InsufficientStockException(this.sku, quantity, available);
        }
        this.reservedQuantity += quantity;
    }

    public synchronized void releaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be positive");
        }
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    public synchronized void deductStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Deduct quantity must be positive");
        }
        this.physicalQuantity = Math.max(0, this.physicalQuantity - quantity);
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    public synchronized void addPhysicalStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Stock quantity addition must be positive");
        }
        this.physicalQuantity += quantity;
    }
}
