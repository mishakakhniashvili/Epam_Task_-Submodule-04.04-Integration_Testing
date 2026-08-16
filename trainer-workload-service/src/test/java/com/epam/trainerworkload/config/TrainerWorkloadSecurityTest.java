package com.epam.trainerworkload.config;

import com.epam.trainerworkload.controller.TrainerWorkloadController;
import com.epam.trainerworkload.dto.TrainerWorkloadResponse;
import com.epam.trainerworkload.filter.TransactionIdFilter;
import com.epam.trainerworkload.service.TrainerWorkloadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrainerWorkloadController.class)
@ContextConfiguration(classes = {
        TrainerWorkloadController.class,
        SecurityConfig.class,
        TransactionIdFilter.class
})
class TrainerWorkloadSecurityTest {

    private static final String WORKLOAD_URL =
            "/api/v1/trainers/john.smith/workload";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrainerWorkloadService trainerWorkloadService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnUnauthorizedWhenJwtIsMissing() throws Exception {
        mockMvc.perform(get(WORKLOAD_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Transaction-Id"));

        verifyNoInteractions(trainerWorkloadService);
    }

    @Test
    void shouldReturnUnauthorizedWhenJwtIsInvalid() throws Exception {
        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new BadJwtException("Invalid JWT"));

        mockMvc.perform(get(WORKLOAD_URL)
                        .header(
                                "Authorization",
                                "Bearer invalid-token"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Transaction-Id"));

        verifyNoInteractions(trainerWorkloadService);
    }

    @Test
    void shouldAllowAuthenticatedUserToReadWorkload()
            throws Exception {
        when(trainerWorkloadService.getTrainerWorkload(
                "john.smith",
                null,
                null
        )).thenReturn(
                new TrainerWorkloadResponse(
                        "john.smith",
                        "John",
                        "Smith",
                        true,
                        List.of()
                )
        );

        mockMvc.perform(get(WORKLOAD_URL)
                        .with(jwt().jwt(token -> token
                                .issuer("gym-crm")
                                .subject("john.smith"))))
                .andExpect(status().isOk());

        verify(trainerWorkloadService).getTrainerWorkload(
                "john.smith",
                null,
                null
        );
    }
}
