package com.epam.trainerworkload.service;

import com.epam.trainerworkload.dto.ActionType;
import com.epam.trainerworkload.dto.TrainerWorkloadRequest;
import com.epam.trainerworkload.entity.ProcessedWorkloadEvent;
import com.epam.trainerworkload.entity.TrainerWorkload;
import com.epam.trainerworkload.repository.ProcessedWorkloadEventRepository;
import com.epam.trainerworkload.repository.TrainerWorkloadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainerWorkloadConcurrencyTest {

    private final Map<String, TrainerWorkload> trainers =
            new ConcurrentHashMap<>();
    private final Set<String> processedEventIds =
            ConcurrentHashMap.newKeySet();

    private TrainerWorkloadService trainerWorkloadService;

    @BeforeEach
    void setUp() {
        trainers.clear();
        processedEventIds.clear();

        TrainerWorkloadRepository trainerRepository =
                mock(TrainerWorkloadRepository.class);
        ProcessedWorkloadEventRepository eventRepository =
                mock(ProcessedWorkloadEventRepository.class);

        when(trainerRepository.findByUsername(any()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        trainers.get(invocation.getArgument(0))
                ));
        when(trainerRepository.save(any(TrainerWorkload.class)))
                .thenAnswer(invocation -> {
                    TrainerWorkload trainer = invocation.getArgument(0);
                    trainers.put(trainer.getUsername(), trainer);
                    return trainer;
                });
        when(eventRepository.existsById(any()))
                .thenAnswer(invocation -> processedEventIds.contains(
                        invocation.getArgument(0)
                ));
        when(eventRepository.save(any(ProcessedWorkloadEvent.class)))
                .thenAnswer(invocation -> {
                    ProcessedWorkloadEvent event = invocation.getArgument(0);
                    processedEventIds.add(event.getEventId());
                    return event;
                });

        TrainerWorkloadTransactionExecutor transactionExecutor =
                new TrainerWorkloadTransactionExecutor(
                        trainerRepository,
                        eventRepository
                );
        trainerWorkloadService = new TrainerWorkloadService(
                trainerRepository,
                transactionExecutor,
                new WorkloadUpdateLockManager()
        );
    }

    @Test
    void concurrentEventsForSameTrainerShouldNotLoseDuration()
            throws Exception {
        runConcurrently(
                request("event-1", 60),
                request("event-2", 30)
        );

        assertEquals(
                90,
                trainerWorkloadService.getMonthlyWorkload(
                        "john.smith",
                        2026,
                        7
                ).trainingSummaryDuration()
        );
    }

    @Test
    void concurrentDuplicateEventShouldBeAppliedOnce()
            throws Exception {
        TrainerWorkloadRequest duplicate = request("same-event", 60);

        runConcurrently(duplicate, duplicate);

        assertEquals(
                60,
                trainerWorkloadService.getMonthlyWorkload(
                        "john.smith",
                        2026,
                        7
                ).trainingSummaryDuration()
        );
    }

    private void runConcurrently(
            TrainerWorkloadRequest first,
            TrainerWorkloadRequest second
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> processAfterStart(first, ready, start)),
                    executor.submit(() -> processAfterStart(second, ready, start))
            );

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void processAfterStart(
            TrainerWorkloadRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();

        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }

        trainerWorkloadService.updateWorkload(request);
    }

    private TrainerWorkloadRequest request(
            String eventId,
            int duration
    ) {
        return TrainerWorkloadRequest.builder()
                .eventId(eventId)
                .trainerUsername("john.smith")
                .trainerFirstName("John")
                .trainerLastName("Smith")
                .active(true)
                .trainingDate(LocalDate.of(2026, 7, 20))
                .trainingDuration(duration)
                .actionType(ActionType.ADD)
                .build();
    }
}
