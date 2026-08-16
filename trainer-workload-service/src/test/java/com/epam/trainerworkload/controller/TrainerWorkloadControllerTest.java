package com.epam.trainerworkload.controller;

import com.epam.trainerworkload.dto.MonthWorkloadResponse;
import com.epam.trainerworkload.dto.MonthlyWorkloadResponse;
import com.epam.trainerworkload.dto.TrainerWorkloadResponse;
import com.epam.trainerworkload.dto.YearWorkloadResponse;
import com.epam.trainerworkload.exception.GlobalExceptionHandler;
import com.epam.trainerworkload.service.TrainerWorkloadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TrainerWorkloadControllerTest {

    @Mock
    private TrainerWorkloadService trainerWorkloadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TrainerWorkloadController controller =
                new TrainerWorkloadController(trainerWorkloadService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnMonthlyWorkload() throws Exception {
        when(trainerWorkloadService.getMonthlyWorkload(
                "john.smith",
                2026,
                7
        )).thenReturn(
                new MonthlyWorkloadResponse(
                        "john.smith",
                        "John",
                        "Smith",
                        true,
                        2026,
                        7,
                        90
                )
        );

        mockMvc.perform(get(
                        "/api/v1/workload-events/john.smith"
                )
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.trainerUsername"
                ).value("john.smith"))
                .andExpect(jsonPath(
                        "$.trainingSummaryDuration"
                ).value(90));
    }

    @Test
    void shouldReturnNestedTrainerWorkloadModel() throws Exception {
        when(trainerWorkloadService.getTrainerWorkload(
                eq("john.smith"),
                eq(2026),
                eq(7)
        )).thenReturn(
                new TrainerWorkloadResponse(
                        "john.smith",
                        "John",
                        "Smith",
                        true,
                        List.of(
                                new YearWorkloadResponse(
                                        2026,
                                        List.of(
                                                new MonthWorkloadResponse(
                                                        7,
                                                        90
                                                )
                                        )
                                )
                        )
                )
        );

        mockMvc.perform(get(
                        "/api/v1/trainers/john.smith/workload"
                )
                        .param("year", "2026")
                        .param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.trainerUsername"
                ).value("john.smith"))
                .andExpect(jsonPath(
                        "$.years[0].year"
                ).value(2026))
                .andExpect(jsonPath(
                        "$.years[0].months[0].trainingSummaryDuration"
                ).value(90));
    }
}
