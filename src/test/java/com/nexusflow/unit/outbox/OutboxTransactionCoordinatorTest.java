package com.nexusflow.unit.outbox;

import com.nexusflow.outbox.domain.OutboxEvent;
import com.nexusflow.outbox.domain.OutboxEventRepository;
import com.nexusflow.outbox.domain.OutboxStatus;
import com.nexusflow.outbox.infrastructure.OutboxTransactionCoordinator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxTransactionCoordinatorTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @InjectMocks
    private OutboxTransactionCoordinator coordinator;

    @Test
    @DisplayName("Should claim pending batch and stamp status to IN_PROGRESS with claimed_at")
    void shouldClaimPendingBatchSuccessfully() {
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .status(OutboxStatus.PENDING)
                .build();

        when(outboxRepository.findPendingEventsForProcessingWithLock(10)).thenReturn(List.of(event));
        when(outboxRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<OutboxEvent> claimed = coordinator.claimPendingBatch(10);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getStatus()).isEqualTo(OutboxStatus.IN_PROGRESS);
        assertThat(claimed.get(0).getClaimedAt()).isNotNull();
        verify(outboxRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Should mark event as published successfully")
    void shouldMarkPublishedSuccessfully() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .status(OutboxStatus.IN_PROGRESS)
                .build();

        when(outboxRepository.findById(eventId)).thenReturn(Optional.of(event));

        coordinator.markPublished(eventId);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxRepository, times(1)).save(event);
    }

    @Test
    @DisplayName("Should recover stuck IN_PROGRESS events beyond lease threshold")
    void shouldRecoverStuckEvents() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(2);
        OutboxEvent stuckEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .status(OutboxStatus.IN_PROGRESS)
                .claimedAt(OffsetDateTime.now().minusMinutes(5))
                .build();

        when(outboxRepository.findStuckEvents(eq(OutboxStatus.IN_PROGRESS), eq(threshold)))
                .thenReturn(List.of(stuckEvent));

        int recoveredCount = coordinator.recoverStuckEvents(threshold);

        assertThat(recoveredCount).isEqualTo(1);
        assertThat(stuckEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(stuckEvent.getClaimedAt()).isNull();
        verify(outboxRepository, times(1)).saveAll(any());
    }
}
