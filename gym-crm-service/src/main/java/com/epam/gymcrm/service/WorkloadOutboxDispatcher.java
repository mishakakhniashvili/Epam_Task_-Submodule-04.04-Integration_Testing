package com.epam.gymcrm.service;

import com.epam.gymcrm.entity.WorkloadOutboxEvent;
import com.epam.gymcrm.messaging.WorkloadEventPublisher;
import com.epam.gymcrm.repository.WorkloadOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkloadOutboxDispatcher {

    private static final String TRANSACTION_ID_MDC_KEY = "transactionId";

    private final WorkloadOutboxEventRepository outboxRepository;
    private final WorkloadEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<String> findReadyEventIds(int batchSize) {
        return outboxRepository.findReadyEventIds(
                clock.instant(),
                PageRequest.of(0, batchSize)
        );
    }

    @Transactional
    public void dispatchEvent(String eventId) {
        outboxRepository.findByIdForUpdate(eventId)
                .ifPresent(this::sendOrReschedule);
    }

    private void sendOrReschedule(WorkloadOutboxEvent event) {
        String previousTransactionId =
                MDC.get(TRANSACTION_ID_MDC_KEY);

        try {
            MDC.put(
                    TRANSACTION_ID_MDC_KEY,
                    event.getTransactionId()
            );

            eventPublisher.publish(event.toRequest());
            outboxRepository.delete(event);

            log.info(
                    "Workload outbox event delivered: eventId={}",
                    event.getEventId()
            );
        } catch (RuntimeException exception) {
            event.scheduleRetry(clock.instant());

            log.warn(
                    "Workload outbox delivery failed: eventId={}, attempt={}, nextAttemptAt={}, message={}",
                    event.getEventId(),
                    event.getAttempts(),
                    event.getNextAttemptAt(),
                    exception.getMessage()
            );
        } finally {
            if (previousTransactionId == null) {
                MDC.remove(TRANSACTION_ID_MDC_KEY);
            } else {
                MDC.put(
                        TRANSACTION_ID_MDC_KEY,
                        previousTransactionId
                );
            }
        }
    }
}
