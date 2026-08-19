package com.epam.trainerworkload.component;

import com.epam.trainerworkload.entity.MonthSummary;
import com.epam.trainerworkload.entity.TrainerWorkload;
import com.epam.trainerworkload.entity.YearSummary;
import com.epam.trainerworkload.repository.TrainerWorkloadRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class TrainerWorkloadSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TrainerWorkloadRepository trainerWorkloadRepository;

    private String trainerUsername;
    private MvcResult latestResponse;

    @Before
    public void resetScenarioState() {
        reset(trainerWorkloadRepository);
        trainerUsername = null;
        latestResponse = null;
    }

    @Given("trainer {string} has {int} minutes of workload in July 2026")
    public void trainerHasMonthlyWorkload(
            String username,
            int duration
    ) {
        trainerUsername = username;

        TrainerWorkload trainer = new TrainerWorkload();
        trainer.setUsername(username);
        trainer.setFirstName("Component");
        trainer.setLastName("Trainer");
        trainer.setActive(true);
        trainer.setYears(List.of(
                new YearSummary(
                        2026,
                        List.of(new MonthSummary(7, duration))
                )
        ));

        when(trainerWorkloadRepository.findByUsername(username))
                .thenReturn(Optional.of(trainer));
    }

    @Given("trainer {string} has no workload")
    public void trainerHasNoWorkload(String username) {
        trainerUsername = username;

        when(trainerWorkloadRepository.findByUsername(username))
                .thenReturn(Optional.empty());
    }

    @When("an authenticated client requests that trainer's workload for July 2026")
    public void requestMonthlyWorkload() throws Exception {
        latestResponse = mockMvc.perform(
                        get(
                                "/api/v1/trainers/{username}/workload",
                                trainerUsername
                        )
                                .param("year", "2026")
                                .param("month", "7")
                                .with(jwt().jwt(token -> token
                                        .issuer("gym-crm")
                                        .subject("bdd-client")))
                )
                .andReturn();
    }

    @Then("the Trainer Workload response status is {int}")
    public void verifyResponseStatus(int expectedStatus) {
        assertThat(latestResponse).isNotNull();
        assertThat(latestResponse.getResponse().getStatus())
                .isEqualTo(expectedStatus);
    }

    @Then("the response reports {int} minutes for July 2026")
    public void verifyMonthlyDuration(int expectedDuration)
            throws Exception {
        JsonNode body = responseBody();

        assertThat(body.path("years").get(0).path("year").asInt())
                .isEqualTo(2026);
        assertThat(
                body.path("years")
                        .get(0)
                        .path("months")
                        .get(0)
                        .path("trainingSummaryDuration")
                        .asInt()
        ).isEqualTo(expectedDuration);
    }

    @Then("the Trainer Workload error message is {string}")
    public void verifyErrorMessage(String expectedMessage)
            throws Exception {
        assertThat(responseBody().path("message").asText())
                .isEqualTo(expectedMessage);
    }

    private JsonNode responseBody() throws Exception {
        assertThat(latestResponse).isNotNull();

        return objectMapper.readTree(
                latestResponse.getResponse().getContentAsString()
        );
    }
}
