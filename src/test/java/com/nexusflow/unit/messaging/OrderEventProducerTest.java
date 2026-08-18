package com.nexusflow.unit.messaging;

import com.nexusflow.messaging.event.OrderCancelledEvent;
import com.nexusflow.messaging.event.OrderCreatedEvent;
import com.nexusflow.messaging.producer.OrderEventProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderEventProducer orderEventProducer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderEventProducer, "orderCreatedTopic", "orders.created");
        ReflectionTestUtils.setField(orderEventProducer, "orderCancelledTopic", "orders.cancelled");
        ReflectionTestUtils.setField(orderEventProducer, "paymentRequestedTopic", "payments.requested");
    }

    @Test
    @DisplayName("Should publish OrderCreatedEvent with partition key and correlation headers")
    void shouldPublishOrderCreatedEvent() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        OrderCreatedEvent event = OrderCreatedEvent.create(
                orderId, customerId, "bruce@wayne.com", BigDecimal.valueOf(1500.00), List.of(), "corr-123"
        );

        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        orderEventProducer.publishOrderCreated(event);

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(1)).send(recordCaptor.capture());

        ProducerRecord<String, Object> capturedRecord = recordCaptor.getValue();
        assertThat(capturedRecord.topic()).isEqualTo("orders.created");
        assertThat(capturedRecord.key()).isEqualTo(orderId.toString());
        assertThat(capturedRecord.headers().lastHeader("correlationId")).isNotNull();
        assertThat(new String(capturedRecord.headers().lastHeader("correlationId").value())).isEqualTo("corr-123");
    }

    @Test
    @DisplayName("Should publish OrderCancelledEvent to orderCancelledTopic")
    void shouldPublishOrderCancelledEvent() {
        UUID orderId = UUID.randomUUID();
        OrderCancelledEvent event = OrderCancelledEvent.create(orderId, "Customer changed mind", "corr-456");

        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        orderEventProducer.publishOrderCancelled(event);

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(1)).send(recordCaptor.capture());

        ProducerRecord<String, Object> capturedRecord = recordCaptor.getValue();
        assertThat(capturedRecord.topic()).isEqualTo("orders.cancelled");
        assertThat(capturedRecord.key()).isEqualTo(orderId.toString());
    }
}
