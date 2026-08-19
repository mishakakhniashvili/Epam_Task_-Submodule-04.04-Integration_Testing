@integration
Feature: Synchronize trainer workload between the microservices
  Training changes accepted by Gym CRM should be delivered to Trainer Workload.

  Scenario: A valid training increases the trainer's monthly workload
    Given a registered trainer and trainee in Gym CRM
    When the trainer creates a 60 minute training dated "2026-07-20"
    Then Gym CRM accepts the training
    And Trainer Workload eventually reports 60 minutes for July 2026

  Scenario: An invalid training is not propagated to Trainer Workload
    Given a registered trainer and trainee in Gym CRM
    When the trainer creates a -15 minute training dated "2026-07-20"
    Then Gym CRM rejects the training as invalid
    And Trainer Workload has no summary for that trainer
