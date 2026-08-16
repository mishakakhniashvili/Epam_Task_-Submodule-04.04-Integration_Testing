package com.epam.trainerworkload.messaging;

import com.epam.trainerworkload.dto.TrainerWorkloadRequest;
import com.epam.trainerworkload.service.TrainerWorkloadService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import static com.epam.trainerworkload.filter.TransactionIdFilter.TRANSACTION_ID_MDC_KEY;

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
        String previousTransactionId = MDC.get("transactionId");
        try {
            if (transactionId == null || transactionId.isBlank()) {
                MDC.remove(TRANSACTION_ID_MDC_KEY);
            } else {
                MDC.put(
                        TRANSACTION_ID_MDC_KEY,
                        transactionId
                );
            }

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
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid workload event JSON",
                    exception
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
