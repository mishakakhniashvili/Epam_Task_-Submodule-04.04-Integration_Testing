package com.epam.trainerworkload.messaging;

import com.epam.trainerworkload.dto.TrainerWorkloadRequest;
import com.epam.trainerworkload.service.TrainerWorkloadService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import static com.epam.trainerworkload.filter.TransactionIdFilter.TRANSACTION_ID_MDC_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkloadEventListener {
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final TrainerWorkloadService trainerWorkloadService;

    @JmsListener(destination = "${workload.queue.name}")
    public void receive(
            String payload,
            @Header(
                    name = "transactionId",
                    required = false
            )
            String transactionId
    ) {
        String previousTransactionId = MDC.get(TRANSACTION_ID_MDC_KEY);
        try {
            if (transactionId == null || transactionId.isBlank()) {
                MDC.remove(TRANSACTION_ID_MDC_KEY);
            } else {
                MDC.put(
                        TRANSACTION_ID_MDC_KEY,
                        transactionId
                );
            }
            log.info("Workload event processing started");

            TrainerWorkloadRequest request =
                    objectMapper.readValue(
                            payload,
                            TrainerWorkloadRequest.class
                    );
            Set<ConstraintViolation<TrainerWorkloadRequest>> violations =
                    validator.validate(request);

            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            if (request.getEventId() == null
                    || request.getEventId().isBlank()) {
                throw new IllegalArgumentException(
                        "eventId is required for workload messages"
                );
            }
            trainerWorkloadService.updateWorkload(request);
            log.info("processing succeeded, including eventId={}, username={}, and action={}",
                    request.getEventId(),
                    request.getTrainerUsername(),
                    request.getActionType());

        } catch (JsonProcessingException exception) {
            log.error("processing failed", exception);
            throw new IllegalArgumentException(
                    "Invalid workload event JSON",
                    exception
            );
        } catch (RuntimeException exception) {
            log.error("Workload event processing failed", exception);
            throw exception;
        }
        finally {
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
