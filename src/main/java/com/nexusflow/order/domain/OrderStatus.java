package com.nexusflow.order.domain;

public enum OrderStatus {
    CREATED,
    WAITING_PAYMENT,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    PAYMENT_FAILED,
    OUT_OF_STOCK
}
