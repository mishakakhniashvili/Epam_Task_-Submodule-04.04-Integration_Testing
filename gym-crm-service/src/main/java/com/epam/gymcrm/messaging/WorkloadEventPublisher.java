package com.epam.gymcrm.messaging;

import com.epam.gymcrm.dto.workload.TrainerWorkloadRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkloadEventPublisher {
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;
    private final String queueName;


    public WorkloadEventPublisher(JmsTemplate jmsTemplate,
                                  ObjectMapper objectMapper,
                                  @Value("${workload.queue.name}") String queueName) {
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = objectMapper;
        this.queueName = queueName;
    }
    public void publish(TrainerWorkloadRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            jmsTemplate.convertAndSend(queueName, payload, message -> {
                message.setStringProperty("eventId", request.getEventId());

                String transactionId = MDC.get("transactionId");

                if (transactionId != null && !transactionId.isBlank()) {
                    message.setStringProperty(
                            "transactionId",
                            transactionId
                    );
                }

                return message;
            });
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize workload event: " + request.getEventId(),
                    exception
            );
        }
    }
}
