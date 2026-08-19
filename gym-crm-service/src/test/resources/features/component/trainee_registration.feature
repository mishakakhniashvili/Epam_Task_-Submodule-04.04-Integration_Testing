@component
Feature: Trainee registration in Gym CRM
  Gym CRM should accept complete registration details and reject invalid details.

  Scenario: Register a trainee with valid details
    When a client registers a trainee with first name "Component" and last name "Trainee"
    Then the Gym CRM response status is 201
    And the registration response contains username "Component.Trainee" and a generated password
    And trainee "Component.Trainee" is stored as active

  Scenario: Reject a trainee registration with a missing first name
    When a client registers a trainee with first name "" and last name "Trainee"
    Then the Gym CRM response status is 400
    And the Gym CRM error message is "firstName is required"
    And no trainee is stored
