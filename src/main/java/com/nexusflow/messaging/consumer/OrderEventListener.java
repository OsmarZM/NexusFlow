package com.nexusflow.messaging.consumer;

import com.nexusflow.messaging.event.OrderCancelledEvent;
import com.nexusflow.messaging.event.OrderCreatedEvent;
import com.nexusflow.messaging.event.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    @KafkaListener(
            topics = "${nexusflow.kafka.topics.order-created:orders.created}",
            groupId = "nexusflow-order-listeners"
    )
    public void handleOrderCreated(
            @Payload OrderCreatedEvent event,
            @Header(name = "correlationId", required = false) String correlationId) {
        log.info("Received OrderCreatedEvent for order ID: {}, total: {}, correlationId: {}",
                event.orderId(), event.totalAmount(), correlationId);
    }

    @KafkaListener(
            topics = "${nexusflow.kafka.topics.order-cancelled:orders.cancelled}",
            groupId = "nexusflow-order-listeners"
    )
    public void handleOrderCancelled(
            @Payload OrderCancelledEvent event,
            @Header(name = "correlationId", required = false) String correlationId) {
        log.info("Received OrderCancelledEvent for order ID: {}, reason: {}, correlationId: {}",
                event.orderId(), event.reason(), correlationId);
    }
}
