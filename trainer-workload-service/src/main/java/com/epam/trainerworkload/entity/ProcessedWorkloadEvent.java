package com.epam.trainerworkload.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor

@Document(collection = "processed_workload_events")
public class ProcessedWorkloadEvent {

    @Id
    private String eventId;

    public ProcessedWorkloadEvent(String eventId) {
        this.eventId = eventId;
    }
}