package com.nexusflow.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    List<InventoryReservation> findByOrderId(UUID orderId);
    Optional<InventoryReservation> findByOrderIdAndSku(UUID orderId, String sku);
    long countBySkuAndStatus(String sku, ReservationStatus status);
}
