package com.epam.gymcrm.component;

import com.epam.gymcrm.entity.Trainee;
import com.epam.gymcrm.repository.TraineeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class TraineeRegistrationSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TraineeRepository traineeRepository;

    private MvcResult latestResponse;

    @Before
    public void resetScenarioState() {
        traineeRepository.deleteAll();
        latestResponse = null;
    }

    @When("a client registers a trainee with first name {string} and last name {string}")
    public void registerTrainee(String firstName, String lastName)
            throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                Map.of(
                        "firstName", firstName,
                        "lastName", lastName
                )
        );

        latestResponse = mockMvc.perform(
                        post("/api/trainees/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andReturn();
    }

    @Then("the Gym CRM response status is {int}")
    public void verifyResponseStatus(int expectedStatus) {
        assertThat(latestResponse).isNotNull();
        assertThat(latestResponse.getResponse().getStatus())
                .isEqualTo(expectedStatus);
    }

    @Then("the registration response contains username {string} and a generated password")
    public void verifyGeneratedCredentials(String expectedUsername)
            throws Exception {
        JsonNode body = responseBody();

        assertThat(body.path("username").asText())
                .isEqualTo(expectedUsername);
        assertThat(body.path("password").asText())
                .isNotBlank();
    }

    @Then("trainee {string} is stored as active")
    public void verifyStoredTrainee(String username) {
        Trainee trainee = traineeRepository
                .findByUserUsername(username)
                .orElseThrow();

        assertThat(trainee.getUser().isActive()).isTrue();
    }

    @Then("the Gym CRM error message is {string}")
    public void verifyErrorMessage(String expectedMessage)
            throws Exception {
        assertThat(responseBody().path("message").asText())
                .isEqualTo(expectedMessage);
    }

    @Then("no trainee is stored")
    public void verifyNoTraineeWasStored() {
        assertThat(traineeRepository.count()).isZero();
    }

    private JsonNode responseBody() throws Exception {
        assertThat(latestResponse).isNotNull();

        return objectMapper.readTree(
                latestResponse.getResponse().getContentAsString()
        );
    }
}
