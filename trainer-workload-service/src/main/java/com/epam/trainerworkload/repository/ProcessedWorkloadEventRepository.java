package com.epam.trainerworkload.repository;

import com.epam.trainerworkload.entity.ProcessedWorkloadEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedWorkloadEventRepository
        extends MongoRepository<ProcessedWorkloadEvent, String> {
}