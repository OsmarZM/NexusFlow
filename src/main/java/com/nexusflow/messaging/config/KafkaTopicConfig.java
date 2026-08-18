package com.nexusflow.messaging.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${nexusflow.kafka.topics.order-created:orders.created}")
    private String orderCreatedTopic;

    @Value("${nexusflow.kafka.topics.order-cancelled:orders.cancelled}")
    private String orderCancelledTopic;

    @Value("${nexusflow.kafka.topics.inventory-reserved:inventory.reserved}")
    private String inventoryReservedTopic;

    @Value("${nexusflow.kafka.topics.inventory-released:inventory.released}")
    private String inventoryReleasedTopic;

    @Value("${nexusflow.kafka.topics.payment-requested:payments.requested}")
    private String paymentRequestedTopic;

    @Value("${nexusflow.kafka.topics.payment-processed:payments.processed}")
    private String paymentProcessedTopic;

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(orderCreatedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(orderCancelledTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name(inventoryReservedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryReleasedTopic() {
        return TopicBuilder.name(inventoryReleasedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentRequestedTopic() {
        return TopicBuilder.name(paymentRequestedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentProcessedTopic() {
        return TopicBuilder.name(paymentProcessedTopic).partitions(3).replicas(1).build();
    }
}
