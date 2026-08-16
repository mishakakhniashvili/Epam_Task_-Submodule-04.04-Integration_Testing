package com.epam.trainerworkload.messaging;

import com.epam.trainerworkload.dto.ActionType;
import com.epam.trainerworkload.dto.TrainerWorkloadRequest;
import com.epam.trainerworkload.service.TrainerWorkloadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.time.LocalDate;

import static com.epam.trainerworkload.filter.TransactionIdFilter.TRANSACTION_ID_MDC_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WorkloadEventListenerTest {

    private ObjectMapper objectMapper;
    private ValidatorFactory validatorFactory;
    private TrainerWorkloadService workloadService;
    private WorkloadEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                );
        validatorFactory =
                Validation.buildDefaultValidatorFactory();
        Validator validator = validatorFactory.getValidator();
        workloadService = mock(TrainerWorkloadService.class);
        listener = new WorkloadEventListener(
                objectMapper,
                validator,
                workloadService
        );
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        validatorFactory.close();
    }

    @Test
    void shouldDeserializeValidateAndProcessMessage()
            throws Exception {
        String payload = objectMapper.writeValueAsString(
                validRequest()
        );
        MDC.put(TRANSACTION_ID_MDC_KEY, "previous-transaction");
        doAnswer(invocation -> {
            assertEquals(
                    "incoming-transaction",
                    MDC.get(TRANSACTION_ID_MDC_KEY)
            );
            return null;
        }).when(workloadService).updateWorkload(any());

        listener.receive(payload, "incoming-transaction");

        ArgumentCaptor<TrainerWorkloadRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        TrainerWorkloadRequest.class
                );
        verify(workloadService).updateWorkload(
                requestCaptor.capture()
        );
        TrainerWorkloadRequest request = requestCaptor.getValue();
        assertEquals("john.smith", request.getTrainerUsername());
        assertEquals(LocalDate.of(2026, 7, 20), request.getTrainingDate());
        assertEquals(60, request.getTrainingDuration());
        assertEquals(ActionType.ADD, request.getActionType());
        assertEquals("event-1", request.getEventId());
        assertEquals(
                "previous-transaction",
                MDC.get(TRANSACTION_ID_MDC_KEY)
        );
    }

    @Test
    void shouldRejectValidJsonWithMissingRequiredInformation()
            throws Exception {
        TrainerWorkloadRequest invalidRequest =
                TrainerWorkloadRequest.builder()
                        .trainerUsername(" ")
                        .trainerFirstName("John")
                        .trainerLastName("Smith")
                        .active(true)
                        .trainingDate(LocalDate.of(2026, 7, 20))
                        .trainingDuration(60)
                        .actionType(ActionType.ADD)
                        .eventId("event-1")
                        .build();
        String payload = objectMapper.writeValueAsString(
                invalidRequest
        );

        assertThrows(
                ConstraintViolationException.class,
                () -> listener.receive(payload, "transaction-123")
        );

        verifyNoInteractions(workloadService);
        assertEquals(null, MDC.get(TRANSACTION_ID_MDC_KEY));
    }

    @Test
    void shouldRejectMessageWithoutEventId() throws Exception {
        TrainerWorkloadRequest requestWithoutEventId =
                TrainerWorkloadRequest.builder()
                        .trainerUsername("john.smith")
                        .trainerFirstName("John")
                        .trainerLastName("Smith")
                        .active(true)
                        .trainingDate(LocalDate.of(2026, 7, 20))
                        .trainingDuration(60)
                        .actionType(ActionType.ADD)
                        .build();
        String payload = objectMapper.writeValueAsString(
                requestWithoutEventId
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> listener.receive(payload, "transaction-123")
        );

        assertEquals(
                "eventId is required for workload messages",
                exception.getMessage()
        );
        verifyNoInteractions(workloadService);
        assertEquals(null, MDC.get(TRANSACTION_ID_MDC_KEY));
    }

    @Test
    void shouldRejectMalformedJsonWithoutProcessingIt() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> listener.receive(
                        "{not-valid-json",
                        "transaction-123"
                )
        );

        assertEquals(
                "Invalid workload event JSON",
                exception.getMessage()
        );
        verifyNoInteractions(workloadService);
        assertEquals(null, MDC.get(TRANSACTION_ID_MDC_KEY));
    }

    private TrainerWorkloadRequest validRequest() {
        return TrainerWorkloadRequest.builder()
                .trainerUsername("john.smith")
                .trainerFirstName("John")
                .trainerLastName("Smith")
                .active(true)
                .trainingDate(LocalDate.of(2026, 7, 20))
                .trainingDuration(60)
                .actionType(ActionType.ADD)
                .eventId("event-1")
                .build();
    }
}
