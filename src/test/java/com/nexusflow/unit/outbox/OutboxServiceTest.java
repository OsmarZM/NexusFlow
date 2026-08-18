package com.nexusflow.unit.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexusflow.messaging.event.PaymentRequestedEvent;
import com.nexusflow.outbox.application.OutboxService;
import com.nexusflow.outbox.domain.OutboxEvent;
import com.nexusflow.outbox.domain.OutboxEventRepository;
import com.nexusflow.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    private ObjectMapper objectMapper;
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        outboxService = new OutboxService(outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("Should serialize domain event and save with PENDING status in outbox table")
    void shouldSaveDomainEventInOutbox() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        PaymentRequestedEvent event = PaymentRequestedEvent.create(orderId, customerId, BigDecimal.valueOf(500.00), "corr-100");

        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> {
            OutboxEvent oe = inv.getArgument(0);
            oe.setId(UUID.randomUUID());
            return oe;
        });

        OutboxEvent saved = outboxService.saveEvent("ORDER", orderId, "payments.requested", event);

        assertThat(saved).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getTopic()).isEqualTo("payments.requested");
        assertThat(saved.getPayload()).contains("PAYMENT_REQUESTED");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCorrelationId()).isEqualTo("corr-100");
    }
}
