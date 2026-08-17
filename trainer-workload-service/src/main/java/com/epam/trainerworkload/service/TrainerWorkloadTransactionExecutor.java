package com.epam.trainerworkload.service;

import com.epam.trainerworkload.dto.ActionType;
import com.epam.trainerworkload.dto.TrainerWorkloadRequest;
import com.epam.trainerworkload.entity.ProcessedWorkloadEvent;
import com.epam.trainerworkload.entity.TrainerWorkload;
import com.epam.trainerworkload.exception.MonthlyWorkloadNotFoundException;
import com.epam.trainerworkload.exception.TrainerWorkloadNotFoundException;
import com.epam.trainerworkload.repository.ProcessedWorkloadEventRepository;
import com.epam.trainerworkload.repository.TrainerWorkloadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.epam.trainerworkload.entity.MonthSummary;
import com.epam.trainerworkload.entity.YearSummary;

import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainerWorkloadTransactionExecutor {

    private final TrainerWorkloadRepository trainerWorkloadRepository;
    private final ProcessedWorkloadEventRepository processedEventRepository;

    public void process(
            TrainerWorkloadRequest request,
            String eventId
    ) {
        if (processedEventRepository.existsById(eventId)) {
            log.info(
                    "Workload event already processed: eventId={}",
                    eventId
            );
            return;
        }

        ActionType actionType = request.getActionType();
        TrainerWorkload trainer = resolveTrainer(
                request,
                actionType
        );

        updateTrainerDetails(trainer, request);

        if (actionType == ActionType.ADD) {
            MonthSummary monthSummary = findOrCreateMonthSummary(request, trainer);

            addDuration(
                    monthSummary,
                    request.getTrainingDuration()
            );
        } else if (actionType == ActionType.DELETE) {
            deleteDuration(request, trainer);
        } else {
            throw new IllegalArgumentException(
                    "Invalid action type"
            );
        }

        trainerWorkloadRepository.save(trainer);


        processedEventRepository.save(
                new ProcessedWorkloadEvent(eventId)
        );

        log.info(
                "Workload updated successfully: eventId={}, trainer={}, action={}",
                eventId,
                request.getTrainerUsername(),
                actionType
        );
    }

    private TrainerWorkload resolveTrainer(
            TrainerWorkloadRequest request,
            ActionType actionType
    ) {
        if (actionType == ActionType.ADD) {
            return findOrCreateTrainer(request);
        }

        if (actionType == ActionType.DELETE) {
            return trainerWorkloadRepository
                    .findByUsername(request.getTrainerUsername())
                    .orElseThrow(() ->
                            new TrainerWorkloadNotFoundException(
                                    "Trainer workload not found: "
                                            + request.getTrainerUsername()
                            )
                    );
        }

        throw new IllegalArgumentException(
                "Invalid action type"
        );
    }

    private TrainerWorkload findOrCreateTrainer(
            TrainerWorkloadRequest request
    ) {
        return trainerWorkloadRepository
                .findByUsername(request.getTrainerUsername())
                .orElseGet(() -> {
                    TrainerWorkload trainer =
                            new TrainerWorkload();

                    trainer.setUsername(
                            request.getTrainerUsername()
                    );
                    trainer.setFirstName(
                            request.getTrainerFirstName()
                    );
                    trainer.setLastName(
                            request.getTrainerLastName()
                    );
                    trainer.setActive(request.getActive());

                    return trainer;
                });
    }

    private void updateTrainerDetails(
            TrainerWorkload trainer,
            TrainerWorkloadRequest request
    ) {
        trainer.setFirstName(request.getTrainerFirstName());
        trainer.setLastName(request.getTrainerLastName());
        trainer.setActive(request.getActive());
    }

    private MonthSummary findOrCreateMonthSummary(
            TrainerWorkloadRequest request,
            TrainerWorkload trainer
    ) {
        int year = request.getTrainingDate().getYear();
        int month = request.getTrainingDate().getMonthValue();

        YearSummary yearSummary = null;
        for(YearSummary i : trainer.getYears()) {
            if(year == i.getYear()) {
                yearSummary = i;
                break;
            }
        }
        if(yearSummary == null) {
            yearSummary = new YearSummary(year, new ArrayList<>());
            trainer.getYears().add(yearSummary);
        }

        MonthSummary monthSummary = null;
        for(MonthSummary i : yearSummary.getMonths()) {
            if(month == i.getMonth()) {
                monthSummary = i;
                break;
            }
        }
        if(monthSummary == null) {
            monthSummary = new MonthSummary(month, 0);
            yearSummary.getMonths().add(monthSummary);
        }

        return monthSummary;
    }

    private void addDuration(
            MonthSummary monthSummary,
            int duration
    ) {
        monthSummary.setTrainingSummaryDuration(
                monthSummary.getTrainingSummaryDuration()
                        + duration
        );
    }

    private void deleteDuration(
            TrainerWorkloadRequest request,
            TrainerWorkload trainer
    ) {
        MonthSummary monthSummary = null;
        int year = request.getTrainingDate().getYear();
        int month = request.getTrainingDate().getMonthValue();
        for(YearSummary i : trainer.getYears()) {
            if(year == i.getYear()) {
                for(MonthSummary j : i.getMonths()) {
                    if (j.getMonth() == month) {
                        monthSummary = j;
                        break;
                    }
                }
            }
        }
        if (monthSummary == null) {
            throw new MonthlyWorkloadNotFoundException("Monthly workload not found: "+ month);
        }

        int updatedDuration =
                monthSummary.getTrainingSummaryDuration()
                        - request.getTrainingDuration();

        if (updatedDuration < 0) {
            throw new IllegalArgumentException(
                    "Training summary duration cannot be negative"
            );
        }

        monthSummary.setTrainingSummaryDuration(
                updatedDuration
        );
    }
}
