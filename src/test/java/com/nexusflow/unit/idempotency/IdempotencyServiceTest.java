package com.nexusflow.unit.idempotency;

import com.nexusflow.idempotency.application.IdempotencyService;
import com.nexusflow.idempotency.domain.ProcessedEvent;
import com.nexusflow.idempotency.domain.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private ProcessedEventRepository repository;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    @DisplayName("Should detect if event was already processed by a consumer")
    void shouldDetectAlreadyProcessedEvent() {
        UUID eventId = UUID.randomUUID();
        String consumer = "OrderSagaOrchestrator";

        when(repository.existsByEventIdAndConsumerName(eventId, consumer)).thenReturn(true);

        boolean result = idempotencyService.isAlreadyProcessed(eventId, consumer);

        assertThat(result).isTrue();
        verify(repository, times(1)).existsByEventIdAndConsumerName(eventId, consumer);
    }

    @Test
    @DisplayName("Should mark event as processed in database")
    void shouldMarkEventAsProcessed() {
        UUID eventId = UUID.randomUUID();
        String consumer = "OrderSagaOrchestrator";

        idempotencyService.markAsProcessed(eventId, "PAYMENT_APPROVED", consumer);

        verify(repository, times(1)).save(any(ProcessedEvent.class));
    }
}
