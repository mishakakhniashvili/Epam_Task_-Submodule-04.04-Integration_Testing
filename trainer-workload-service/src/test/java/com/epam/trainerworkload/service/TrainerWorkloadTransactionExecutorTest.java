package com.epam.trainerworkload.service;

import com.epam.trainerworkload.dto.ActionType;
import com.epam.trainerworkload.dto.TrainerWorkloadRequest;
import com.epam.trainerworkload.entity.MonthSummary;
import com.epam.trainerworkload.entity.ProcessedWorkloadEvent;
import com.epam.trainerworkload.entity.TrainerWorkload;
import com.epam.trainerworkload.entity.YearSummary;
import com.epam.trainerworkload.exception.MonthlyWorkloadNotFoundException;
import com.epam.trainerworkload.exception.TrainerWorkloadNotFoundException;
import com.epam.trainerworkload.repository.ProcessedWorkloadEventRepository;
import com.epam.trainerworkload.repository.TrainerWorkloadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainerWorkloadTransactionExecutorTest {

    @Mock
    private TrainerWorkloadRepository trainerWorkloadRepository;

    @Mock
    private ProcessedWorkloadEventRepository processedEventRepository;

    private TrainerWorkloadTransactionExecutor transactionExecutor;

    @BeforeEach
    void setUp() {
        transactionExecutor = new TrainerWorkloadTransactionExecutor(
                trainerWorkloadRepository,
                processedEventRepository
        );
    }

    @Test
    void shouldCreateNestedWorkloadForFirstAddEvent() {
        TrainerWorkloadRequest request = request(
                ActionType.ADD,
                60,
                LocalDate.of(2026, 7, 15)
        );
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.empty());

        transactionExecutor.process(request, "event-1");

        ArgumentCaptor<TrainerWorkload> trainerCaptor =
                ArgumentCaptor.forClass(TrainerWorkload.class);
        verify(trainerWorkloadRepository).save(trainerCaptor.capture());

        TrainerWorkload savedTrainer = trainerCaptor.getValue();
        assertEquals("john.smith", savedTrainer.getUsername());
        assertEquals("John", savedTrainer.getFirstName());
        assertEquals("Smith", savedTrainer.getLastName());
        assertTrue(savedTrainer.isActive());
        assertEquals(1, savedTrainer.getYears().size());
        assertEquals(2026, savedTrainer.getYears().get(0).getYear());
        assertEquals(1, savedTrainer.getYears().get(0).getMonths().size());
        assertEquals(7, savedTrainer.getYears().get(0).getMonths().get(0).getMonth());
        assertEquals(
                60,
                savedTrainer.getYears().get(0).getMonths().get(0)
                        .getTrainingSummaryDuration()
        );

        ArgumentCaptor<ProcessedWorkloadEvent> eventCaptor =
                ArgumentCaptor.forClass(ProcessedWorkloadEvent.class);
        verify(processedEventRepository).save(eventCaptor.capture());
        assertEquals("event-1", eventCaptor.getValue().getEventId());
    }

    @Test
    void shouldAddDurationToExistingMonth() {
        TrainerWorkload trainer = trainerWithMonth(2026, 7, 60);
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.of(trainer));

        transactionExecutor.process(
                request(ActionType.ADD, 30, LocalDate.of(2026, 7, 20)),
                "event-1"
        );

        MonthSummary month = trainer.getYears().get(0).getMonths().get(0);
        assertEquals(90, month.getTrainingSummaryDuration());
        assertEquals("John", trainer.getFirstName());
        assertEquals("Smith", trainer.getLastName());
        assertTrue(trainer.isActive());
        verify(trainerWorkloadRepository).save(trainer);
    }

    @Test
    void shouldAddANewMonthToAnExistingYear() {
        TrainerWorkload trainer = trainerWithMonth(2026, 6, 45);
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.of(trainer));

        transactionExecutor.process(
                request(ActionType.ADD, 30, LocalDate.of(2026, 7, 20)),
                "event-1"
        );

        List<MonthSummary> months = trainer.getYears().get(0).getMonths();
        assertEquals(2, months.size());
        assertEquals(7, months.get(1).getMonth());
        assertEquals(30, months.get(1).getTrainingSummaryDuration());
    }

    @Test
    void shouldSubtractDurationForDeleteEvent() {
        TrainerWorkload trainer = trainerWithMonth(2026, 7, 90);
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.of(trainer));

        transactionExecutor.process(
                request(ActionType.DELETE, 30, LocalDate.of(2026, 7, 20)),
                "event-1"
        );

        assertEquals(
                60,
                trainer.getYears().get(0).getMonths().get(0)
                        .getTrainingSummaryDuration()
        );
        verify(trainerWorkloadRepository).save(trainer);
    }

    @Test
    void shouldRejectDeleteThatWouldMakeDurationNegative() {
        TrainerWorkload trainer = trainerWithMonth(2026, 7, 60);
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.of(trainer));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionExecutor.process(
                        request(ActionType.DELETE, 90, LocalDate.of(2026, 7, 20)),
                        "event-1"
                )
        );

        assertEquals(
                "Training summary duration cannot be negative",
                exception.getMessage()
        );
        assertEquals(
                60,
                trainer.getYears().get(0).getMonths().get(0)
                        .getTrainingSummaryDuration()
        );
        verify(trainerWorkloadRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void shouldRejectDeleteWhenTrainerDoesNotExist() {
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.empty());

        assertThrows(
                TrainerWorkloadNotFoundException.class,
                () -> transactionExecutor.process(
                        request(ActionType.DELETE, 30, LocalDate.of(2026, 7, 20)),
                        "event-1"
                )
        );

        verify(trainerWorkloadRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void shouldRejectDeleteWhenMonthDoesNotExist() {
        TrainerWorkload trainer = trainerWithMonth(2026, 6, 60);
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.of(trainer));

        assertThrows(
                MonthlyWorkloadNotFoundException.class,
                () -> transactionExecutor.process(
                        request(ActionType.DELETE, 30, LocalDate.of(2026, 7, 20)),
                        "event-1"
                )
        );

        verify(trainerWorkloadRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void shouldIgnoreAnAlreadyProcessedEvent() {
        TrainerWorkloadRequest request = request(
                ActionType.ADD,
                60,
                LocalDate.of(2026, 7, 20)
        );
        when(processedEventRepository.existsById("event-1"))
                .thenReturn(true);

        transactionExecutor.process(request, "event-1");

        verify(processedEventRepository).existsById("event-1");
        verifyNoInteractions(trainerWorkloadRepository);
        verify(processedEventRepository, never()).save(any());
    }

    private TrainerWorkloadRequest request(
            ActionType actionType,
            int duration,
            LocalDate trainingDate
    ) {
        return TrainerWorkloadRequest.builder()
                .trainerUsername("john.smith")
                .trainerFirstName("John")
                .trainerLastName("Smith")
                .active(true)
                .trainingDate(trainingDate)
                .trainingDuration(duration)
                .actionType(actionType)
                .eventId("event-1")
                .build();
    }

    private TrainerWorkload trainerWithMonth(
            int year,
            int month,
            int duration
    ) {
        TrainerWorkload trainer = new TrainerWorkload();
        trainer.setUsername("john.smith");
        trainer.setFirstName("Old");
        trainer.setLastName("Name");
        trainer.setActive(false);

        MonthSummary monthSummary = new MonthSummary(month, duration);
        YearSummary yearSummary = new YearSummary(
                year,
                new ArrayList<>(List.of(monthSummary))
        );
        trainer.setYears(new ArrayList<>(List.of(yearSummary)));
        return trainer;
    }
}
