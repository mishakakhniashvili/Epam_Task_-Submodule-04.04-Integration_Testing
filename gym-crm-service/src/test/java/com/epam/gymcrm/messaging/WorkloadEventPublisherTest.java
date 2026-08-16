package com.epam.gymcrm.messaging;

import com.epam.gymcrm.dto.workload.ActionType;
import com.epam.gymcrm.dto.workload.TrainerWorkloadRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkloadEventPublisherTest {

    private static final String QUEUE_NAME =
            "trainer.workload.events";

    private JmsTemplate jmsTemplate;
    private ObjectMapper objectMapper;
    private WorkloadEventPublisher publisher;

    @BeforeEach
    void setUp() {
        jmsTemplate = mock(JmsTemplate.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                );
        publisher = new WorkloadEventPublisher(
                jmsTemplate,
                objectMapper,
                QUEUE_NAME
        );
        MDC.clear();
    }

    @Test
    void shouldPublishJsonPayloadWithTracingProperties()
            throws Exception {
        TrainerWorkloadRequest request = createRequest();
        MDC.put("transactionId", "transaction-123");

        try {
            publisher.publish(request);

            var payloadCaptor =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            var processorCaptor =
                    org.mockito.ArgumentCaptor.forClass(
                            MessagePostProcessor.class
                    );

            verify(jmsTemplate).convertAndSend(
                    eq(QUEUE_NAME),
                    payloadCaptor.capture(),
                    processorCaptor.capture()
            );

            JsonNode payload = objectMapper.readTree(
                    payloadCaptor.getValue()
            );
            assertEquals(
                    "john.smith",
                    payload.get("trainerUsername").asText()
            );
            assertEquals(
                    "2026-07-20",
                    payload.get("trainingDate").asText()
            );
            assertEquals(
                    "ADD",
                    payload.get("actionType").asText()
            );
            assertEquals(
                    "event-1",
                    payload.get("eventId").asText()
            );

            Message message = mock(Message.class);
            Message processedMessage = processorCaptor
                    .getValue()
                    .postProcessMessage(message);

            assertSame(message, processedMessage);
            verify(message).setStringProperty(
                    "eventId",
                    "event-1"
            );
            verify(message).setStringProperty(
                    "transactionId",
                    "transaction-123"
            );
        } finally {
            MDC.clear();
        }
    }

    @Test
    void shouldOmitTransactionPropertyWhenMdcValueIsMissing()
            throws Exception {
        TrainerWorkloadRequest request = createRequest();

        publisher.publish(request);

        var processorCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        MessagePostProcessor.class
                );
        verify(jmsTemplate).convertAndSend(
                eq(QUEUE_NAME),
                anyString(),
                processorCaptor.capture()
        );

        Message message = mock(Message.class);
        processorCaptor.getValue().postProcessMessage(message);

        verify(message).setStringProperty("eventId", "event-1");
        verify(message, never()).setStringProperty(
                eq("transactionId"),
                anyString()
        );
    }

    @Test
    void shouldFailWithoutSendingWhenSerializationFails()
            throws JsonProcessingException {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        WorkloadEventPublisher failingPublisher =
                new WorkloadEventPublisher(
                        jmsTemplate,
                        failingObjectMapper,
                        QUEUE_NAME
                );
        TrainerWorkloadRequest request = createRequest();
        JsonProcessingException serializationFailure =
                new JsonProcessingException("serialization failed") {
                };

        when(failingObjectMapper.writeValueAsString(request))
                .thenThrow(serializationFailure);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> failingPublisher.publish(request)
        );

        assertEquals(
                "Could not serialize workload event: event-1",
                exception.getMessage()
        );
        assertSame(serializationFailure, exception.getCause());
        verifyNoInteractions(jmsTemplate);
    }

    private TrainerWorkloadRequest createRequest() {
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
