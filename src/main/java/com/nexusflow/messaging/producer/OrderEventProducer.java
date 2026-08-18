package com.nexusflow.messaging.producer;

import com.nexusflow.messaging.event.DomainEvent;
import com.nexusflow.messaging.event.OrderCancelledEvent;
import com.nexusflow.messaging.event.OrderCreatedEvent;
import com.nexusflow.messaging.event.PaymentRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${nexusflow.kafka.topics.order-created:orders.created}")
    private String orderCreatedTopic;

    @Value("${nexusflow.kafka.topics.order-cancelled:orders.cancelled}")
    private String orderCancelledTopic;

    @Value("${nexusflow.kafka.topics.payment-requested:payments.requested}")
    private String paymentRequestedTopic;

    public void publishOrderCreated(OrderCreatedEvent event) {
        publishEvent(orderCreatedTopic, event.orderId().toString(), event);
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        publishEvent(orderCancelledTopic, event.orderId().toString(), event);
    }

    public void publishPaymentRequested(PaymentRequestedEvent event) {
        publishEvent(paymentRequestedTopic, event.orderId().toString(), event);
    }

    private void publishEvent(String topic, String partitionKey, DomainEvent event) {
        log.info("Publishing event {} [{}] to topic {} with key: {}",
                event.getEventType(), event.getEventId(), topic, partitionKey);

        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, partitionKey, event);
        record.headers().add("correlationId", event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventId", event.getEventId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event {} to topic {}: {}", event.getEventId(), topic, ex.getMessage(), ex);
            } else {
                log.info("Event {} successfully published to topic {} [partition: {}, offset: {}]",
                        event.getEventId(), topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
