package com.epam.trainerworkload.component;

import com.epam.trainerworkload.repository.ProcessedWorkloadEventRepository;
import com.epam.trainerworkload.repository.TrainerWorkloadRepository;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(properties = {
        "spring.data.mongodb.auto-index-creation=false",
        "spring.jms.listener.auto-startup=false",
        "eureka.client.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TrainerWorkloadComponentTestConfiguration {

    @MockBean
    private TrainerWorkloadRepository trainerWorkloadRepository;

    @MockBean
    private ProcessedWorkloadEventRepository processedWorkloadEventRepository;
}
