# Gym CRM microservices

This project contains:

- `discovery-service` — Eureka server on port `8761`;
- `gym-crm-service` — the main CRM API on port `8080` and workload-event producer;
- `trainer-workload-service` — the MongoDB-backed workload-event consumer and query API on port `8081`;
- ActiveMQ — the message broker connecting the two application services.

## Required configuration

Set the same Base64-encoded, minimum 32-byte JWT secret for both application
services:

```powershell
$env:JWT_SECRET_BASE64="<base64-encoded-secret>"
```

For the main service, configure PostgreSQL credentials:

```powershell
$env:GYM_DB_USERNAME="postgres"
$env:GYM_DB_PASSWORD="<database-password>"
```

`GYM_DB_URL` is optional. Each Spring profile supplies a default local database
URL. Available profiles are `local`, `dev`, `stg`, and `prod`.

The application services use these optional ActiveMQ environment variables:

```powershell
$env:ACTIVEMQ_BROKER_URL="tcp://localhost:61616"
$env:ACTIVEMQ_USER="admin"
$env:ACTIVEMQ_PASSWORD="admin"
$env:WORKLOAD_QUEUE_NAME="trainer.workload.events"
```

These values are already the local defaults, so they only need to be set when
the broker configuration is different. No production secret or database
password is stored in this repository.

The workload service stores trainer workload summaries in MongoDB. Its URI is
also configurable and defaults to the local `trainer_workload` database:

```powershell
$env:MONGODB_URI="mongodb://localhost:27017/trainer_workload"
```

## Build and test

```powershell
mvn clean test
mvn package -DskipTests
```

All three packaged JARs are executable.

## BDD test layers

The Cucumber tests are split by test boundary:

- `gym-crm-service` contains Gym CRM component scenarios. They exercise the
  real HTTP, validation, service, security, and H2 persistence stack while
  keeping external messaging out of the component boundary.
- `trainer-workload-service` contains Trainer Workload component scenarios.
  They exercise the real HTTP, security, controller, and service stack while
  replacing the external MongoDB repositories with mocks.
- `integration-tests` starts both real application contexts on random ports
  and verifies the complete HTTP -> outbox -> ActiveMQ -> consumer -> MongoDB
  flow. H2, an embedded ActiveMQ broker, and an in-memory Mongo-compatible
  server are created by the test, so Docker is not required.

Every feature contains both a positive scenario and a negative scenario. Run
one layer while developing with:

```powershell
mvn -f gym-crm-service/pom.xml "-Dtest=RunGymCrmComponentTest" test
mvn -f trainer-workload-service/pom.xml "-Dtest=RunTrainerWorkloadComponentTest" test
mvn -pl integration-tests -am test "-Dtest=RunMicroservicesIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Run all unit, component, and integration tests together with `mvn clean test`.

## Start order

1. Start ActiveMQ. To create the local container the first time:

   ```powershell
   docker run --name gym-activemq -p 61616:61616 -p 8161:8161 apache/activemq:latest
   ```

   On later runs, start the existing container with:

   ```powershell
   docker start gym-activemq
   ```

2. Start MongoDB on port `27017`. Use an existing local MongoDB service, or
   create a container the first time:

   ```powershell
   docker run --name gym-mongodb -p 27017:27017 -d mongo:7
   ```

   On later runs, start the existing container with:

   ```powershell
   docker start gym-mongodb
   ```

3. Start Eureka:

   ```powershell
   java -jar discovery-service/target/discovery-service-1.0-SNAPSHOT.jar
   ```

4. Start the workload service:

   ```powershell
   java -jar trainer-workload-service/target/trainer-workload-service-1.0-SNAPSHOT.jar
   ```

5. Start the main service with a database profile:

   ```powershell
   java -jar gym-crm-service/target/gym-crm-spring-boot-1.0-SNAPSHOT.jar --spring.profiles.active=local
   ```

The Eureka dashboard is available at `http://localhost:8761`. The ActiveMQ
console is available at `http://localhost:8161`.

## Workload API

Workload updates are internal asynchronous messages; there is no public HTTP
write endpoint. When a training is created or a trainee is deleted, the main
service stores an outbox event in the same database transaction. The outbox
dispatcher publishes its JSON payload to the `trainer.workload.events` queue,
and the workload service consumes it.

Retrieve the nested trainer/year/month model:

```http
GET /api/v1/trainers/{username}/workload
GET /api/v1/trainers/{username}/workload?year=2026&month=7
Authorization: Bearer <JWT>
```

The legacy flat monthly endpoint remains available:

```http
GET /api/v1/workload-events/{username}?year=2026&month=7
Authorization: Bearer <JWT>
```

## Delivery guarantees

Training creation and trainee deletion store workload events in the main
database transaction. A scheduled outbox dispatcher sends pending events to
ActiveMQ with the event ID and originating transaction ID. Failed sends remain
in the outbox and are retried later.

The workload service validates each message, records processed `eventId` values
in MongoDB for idempotency, and serializes updates for the same trainer while it
updates the embedded year/month summary. Messages that repeatedly fail
consumption are routed by ActiveMQ to `ActiveMQ.DLQ` after the broker's
redelivery limit is reached.
