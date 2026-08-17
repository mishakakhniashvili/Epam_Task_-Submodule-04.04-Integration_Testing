package com.epam.trainerworkload.service;

import com.epam.trainerworkload.dto.MonthWorkloadResponse;
import com.epam.trainerworkload.dto.MonthlyWorkloadResponse;
import com.epam.trainerworkload.dto.TrainerWorkloadRequest;
import com.epam.trainerworkload.dto.TrainerWorkloadResponse;
import com.epam.trainerworkload.dto.YearWorkloadResponse;
import com.epam.trainerworkload.entity.TrainerWorkload;
import com.epam.trainerworkload.entity.MonthSummary;
import com.epam.trainerworkload.entity.YearSummary;
import com.epam.trainerworkload.exception.TrainerWorkloadNotFoundException;
import com.epam.trainerworkload.repository.TrainerWorkloadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainerWorkloadService {

    private final TrainerWorkloadRepository trainerWorkloadRepository;
    private final TrainerWorkloadTransactionExecutor transactionExecutor;
    private final WorkloadUpdateLockManager lockManager;

    public void updateWorkload(TrainerWorkloadRequest request) {
        String eventId = resolveEventId(request.getEventId());

        lockManager.execute(
                request.getTrainerUsername(),
                eventId,
                () -> transactionExecutor.process(request, eventId)
        );
    }

    public MonthlyWorkloadResponse getMonthlyWorkload(
            String username,
            int year,
            int month
    ) {
        TrainerWorkload trainer = findTrainer(username);
        int duration =  findDuration(trainer, year, month);

        return new MonthlyWorkloadResponse(
                trainer.getUsername(),
                trainer.getFirstName(),
                trainer.getLastName(),
                trainer.isActive(),
                year,
                month,
                duration
        );
    }

    public TrainerWorkloadResponse getTrainerWorkload(
            String username,
            Integer year,
            Integer month
    ) {
        if ((year == null) != (month == null)) {
            throw new IllegalArgumentException(
                    "Year and month must be provided together"
            );
        }

        TrainerWorkload trainer = findTrainer(username);

        List<YearWorkloadResponse> years;

        if (year == null) {
            years = toYearResponses(trainer.getYears());
        } else {
            int duration = findDuration(trainer, year, month);

            MonthWorkloadResponse monthResponse =
                    new MonthWorkloadResponse(month, duration);

            YearWorkloadResponse yearResponse =
                    new YearWorkloadResponse(
                            year,
                            List.of(monthResponse)
                    );

            years = List.of(yearResponse);
        }
        return new TrainerWorkloadResponse(
                trainer.getUsername(),
                trainer.getFirstName(),
                trainer.getLastName(),
                trainer.isActive(),
                years
        );
    }

    private List<YearWorkloadResponse> toYearResponses(
            List<YearSummary> summaries
    ) {
        List<YearWorkloadResponse> responses = new ArrayList<>();

        for (YearSummary yearSummary : summaries) {
            List<MonthWorkloadResponse> monthResponses = new ArrayList<>();
            for(MonthSummary monthSummary : yearSummary.getMonths()) {
                monthResponses.add(new MonthWorkloadResponse(monthSummary.getMonth(),monthSummary.getTrainingSummaryDuration()));
            }
            responses.add(new YearWorkloadResponse(yearSummary.getYear(),monthResponses));
        }
        return responses;
    }

    private TrainerWorkload findTrainer(String username) {
        return trainerWorkloadRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new TrainerWorkloadNotFoundException(
                                "Trainer workload not found: "
                                        + username
                        )
                );
    }

    private String resolveEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return eventId;
    }
    private int findDuration(
            TrainerWorkload trainer,
            int year,
            int month
    ){
        int duration;

        for(YearSummary yearSummary : trainer.getYears()) {
            if (year == yearSummary.getYear()) {
                for(MonthSummary monthSummary : yearSummary.getMonths()) {
                    if(monthSummary.getMonth() == month){
                        duration = monthSummary.getTrainingSummaryDuration();
                        return duration;
                    }
                }            }
        }
        return 0;
    }
}
