package com.epam.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class MicroservicesIntegrationSteps {

    private static final IntegrationTestEnvironment ENVIRONMENT =
            new IntegrationTestEnvironment();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().findAndRegisterModules();

    private String trainerUsername;
    private String traineeUsername;
    private String accessToken;
    private HttpResponse<String> trainingResponse;

    @BeforeAll
    public static void startApplications() throws Exception {
        ENVIRONMENT.start();
    }

    @AfterAll
    public static void stopApplications() throws Exception {
        ENVIRONMENT.close();
    }

    @Before
    public void resetScenarioState() {
        trainerUsername = null;
        traineeUsername = null;
        accessToken = null;
        trainingResponse = null;
    }

    @Given("a registered trainer and trainee in Gym CRM")
    public void registerTrainerAndTrainee() throws Exception {
        String uniqueSuffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8);

        HttpResponse<String> traineeRegistration = post(
                ENVIRONMENT.gymBaseUrl() + "/api/trainees/register",
                Map.of(
                        "firstName", "Trainee" + uniqueSuffix,
                        "lastName", "Bdd"
                ),
                null
        );
        assertStatus(traineeRegistration, 201);
        traineeUsername = json(traineeRegistration)
                .path("username")
                .asText();

        HttpResponse<String> trainerRegistration = post(
                ENVIRONMENT.gymBaseUrl() + "/api/trainers/register",
                Map.of(
                        "firstName", "Trainer" + uniqueSuffix,
                        "lastName", "Bdd",
                        "specialization", "Fitness"
                ),
                null
        );
        assertStatus(trainerRegistration, 201);

        JsonNode trainerCredentials = json(trainerRegistration);
        trainerUsername = trainerCredentials.path("username").asText();
        String trainerPassword = trainerCredentials.path("password").asText();

        HttpResponse<String> loginResponse = post(
                ENVIRONMENT.gymBaseUrl() + "/api/login",
                Map.of(
                        "username", trainerUsername,
                        "password", trainerPassword
                ),
                null
        );
        assertStatus(loginResponse, 200);
        accessToken = json(loginResponse).path("accessToken").asText();

        assertThat(accessToken).isNotBlank();
    }

    @When("the trainer creates a {int} minute training dated {string}")
    public void createTraining(int duration, String trainingDate)
            throws Exception {
        trainingResponse = post(
                ENVIRONMENT.gymBaseUrl() + "/api/trainings",
                Map.of(
                        "traineeUsername", traineeUsername,
                        "trainingName", "BDD integration training",
                        "trainingDate", trainingDate,
                        "trainingDuration", duration
                ),
                accessToken
        );
    }

    @Then("Gym CRM accepts the training")
    public void verifyTrainingWasAccepted() {
        assertStatus(trainingResponse, 200);
    }

    @Then("Gym CRM rejects the training as invalid")
    public void verifyTrainingWasRejected() {
        assertStatus(trainingResponse, 400);
    }

    @Then("Trainer Workload eventually reports {int} minutes for July 2026")
    public void verifyWorkloadWasSynchronized(int expectedDuration)
            throws Exception {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(5).toNanos();
        HttpResponse<String> latestResponse = null;

        while (System.nanoTime() < deadline) {
            latestResponse = getTrainerWorkload();

            if (latestResponse.statusCode() == 200) {
                int actualDuration = json(latestResponse)
                        .path("years")
                        .get(0)
                        .path("months")
                        .get(0)
                        .path("trainingSummaryDuration")
                        .asInt();

                if (actualDuration == expectedDuration) {
                    return;
                }
            }

            Thread.sleep(50);
        }

        String lastResult = latestResponse == null
                ? "no response"
                : latestResponse.statusCode() + ": " + latestResponse.body();

        throw new AssertionError(
                "Trainer workload was not synchronized. Last response: "
                        + lastResult
        );
    }

    @Then("Trainer Workload has no summary for that trainer")
    public void verifyNoWorkloadWasCreated() throws Exception {
        Thread.sleep(250);

        HttpResponse<String> response = getTrainerWorkload();
        assertStatus(response, 404);
    }

    private HttpResponse<String> post(
            String url,
            Map<String, Object> body,
            String bearerToken
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        OBJECT_MAPPER.writeValueAsString(body)
                ));

        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }

        return HTTP_CLIENT.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> getTrainerWorkload()
            throws Exception {
        String encodedUsername = URLEncoder.encode(
                trainerUsername,
                StandardCharsets.UTF_8
        );
        String url = ENVIRONMENT.workloadBaseUrl()
                + "/api/v1/trainers/"
                + encodedUsername
                + "/workload?year=2026&month=7";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        return HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private JsonNode json(HttpResponse<String> response)
            throws Exception {
        return OBJECT_MAPPER.readTree(response.body());
    }

    private void assertStatus(
            HttpResponse<String> response,
            int expectedStatus
    ) {
        assertThat(response)
                .as("An HTTP response should be available")
                .isNotNull();
        assertThat(response.statusCode())
                .as("HTTP response body: %s", response.body())
                .isEqualTo(expectedStatus);
    }
}
