package com.epam.trainerworkload.controller;
import com.epam.trainerworkload.dto.MonthlyWorkloadResponse;
import com.epam.trainerworkload.dto.TrainerWorkloadResponse;
import com.epam.trainerworkload.service.TrainerWorkloadService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class TrainerWorkloadController {

    private final TrainerWorkloadService trainerWorkloadService;

    @GetMapping("/workload-events/{username}")
    public ResponseEntity<MonthlyWorkloadResponse> getMonthlyWorkload(
            @PathVariable
            @NotBlank
            String username,

            @RequestParam
            @Min(1)
            int year,

            @RequestParam
            @Min(1)
            @Max(12)
            int month
    ) {
        MonthlyWorkloadResponse response =
                trainerWorkloadService.getMonthlyWorkload(
                        username,
                        year,
                        month
                );

        log.info(
                "Operation getMonthlyWorkload response: trainer={}, year={}, month={}, duration={}, status=200",
                username,
                year,
                month,
                response.trainingSummaryDuration()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/trainers/{username}/workload")
    public ResponseEntity<TrainerWorkloadResponse> getTrainerWorkload(
            @PathVariable
            @NotBlank
            String username,

            @RequestParam(required = false)
            @Min(1)
            Integer year,

            @RequestParam(required = false)
            @Min(1)
            @Max(12)
            Integer month
    ) {
        log.info(
                "Operation getTrainerWorkload request: trainer={}, year={}, month={}",
                username,
                year,
                month
        );

        TrainerWorkloadResponse response =
                trainerWorkloadService.getTrainerWorkload(
                        username,
                        year,
                        month
                );

        log.info(
                "Operation getTrainerWorkload response: trainer={}, years={}, status=200",
                username,
                response.years().size()
        );

        return ResponseEntity.ok(response);
    }
}
