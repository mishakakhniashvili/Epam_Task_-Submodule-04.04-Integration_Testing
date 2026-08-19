@component
Feature: Read trainer workload
  Trainer Workload should expose stored summaries and report missing trainers.

  Scenario: Read an existing trainer's monthly workload
    Given trainer "component.trainer" has 90 minutes of workload in July 2026
    When an authenticated client requests that trainer's workload for July 2026
    Then the Trainer Workload response status is 200
    And the response reports 90 minutes for July 2026

  Scenario: Request workload for an unknown trainer
    Given trainer "unknown.trainer" has no workload
    When an authenticated client requests that trainer's workload for July 2026
    Then the Trainer Workload response status is 404
    And the Trainer Workload error message is "Trainer workload not found: unknown.trainer"
