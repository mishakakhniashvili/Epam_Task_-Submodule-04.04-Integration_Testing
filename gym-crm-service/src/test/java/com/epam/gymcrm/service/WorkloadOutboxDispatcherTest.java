package com.epam.gymcrm.service;

import com.epam.gymcrm.dto.workload.ActionType;
import com.epam.gymcrm.entity.Trainer;
import com.epam.gymcrm.entity.Training;
import com.epam.gymcrm.entity.TrainingType;
import com.epam.gymcrm.entity.User;
import com.epam.gymcrm.entity.WorkloadOutboxEvent;
import com.epam.gymcrm.messaging.WorkloadEventPublisher;
import com.epam.gymcrm.repository.WorkloadOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkloadOutboxDispatcherTest {

    private WorkloadOutboxEventRepository outboxRepository;
    private WorkloadEventPublisher eventPublisher;
    private WorkloadOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        outboxRepository =
                mock(WorkloadOutboxEventRepository.class);
        eventPublisher =
                mock(WorkloadEventPublisher.class);

        dispatcher = new WorkloadOutboxDispatcher(
                outboxRepository,
                eventPublisher,
                Clock.fixed(
                        Instant.parse("2026-07-25T00:00:00Z"),
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    void shouldDeleteOutboxEventAfterSuccessfulDelivery() {
        WorkloadOutboxEvent event = createEvent();

        when(outboxRepository.findByIdForUpdate(event.getEventId()))
                .thenReturn(Optional.of(event));

        dispatcher.dispatchEvent(event.getEventId());

        verify(eventPublisher).publish(any());
        verify(outboxRepository).delete(event);
    }

    @Test
    void shouldRetainAndRescheduleEventAfterFailure() {
        WorkloadOutboxEvent event = createEvent();

        when(outboxRepository.findByIdForUpdate(event.getEventId()))
                .thenReturn(Optional.of(event));

        doThrow(new RuntimeException("workload unavailable"))
                .when(eventPublisher)
                .publish(any());

        dispatcher.dispatchEvent(event.getEventId());

        assertEquals(1, event.getAttempts());
        assertEquals(
                Instant.parse("2026-07-25T00:00:02Z"),
                event.getNextAttemptAt()
        );
        verify(outboxRepository, never()).delete(event);
    }

    private WorkloadOutboxEvent createEvent() {
        TrainingType trainingType =
                new TrainingType("Fitness");
        Trainer trainer = new Trainer(
                new User(
                        "John",
                        "Smith",
                        "john.smith",
                        "encoded-password",
                        true
                ),
                trainingType
        );

        Training training = new Training(
                "Morning training",
                LocalDate.of(2026, 7, 20),
                trainer,
                mock(com.epam.gymcrm.entity.Trainee.class),
                trainingType,
                60
        );
        training.setId(1L);

        return WorkloadOutboxEvent.fromTraining(
                training,
                ActionType.ADD,
                "transaction-123",
                Instant.parse("2026-07-25T00:00:00Z")
        );
    }
}
