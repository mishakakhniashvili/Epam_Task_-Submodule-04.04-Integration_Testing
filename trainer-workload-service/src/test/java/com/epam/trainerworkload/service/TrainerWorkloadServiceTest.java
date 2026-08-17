package com.epam.trainerworkload.service;

import com.epam.trainerworkload.dto.MonthlyWorkloadResponse;
import com.epam.trainerworkload.dto.TrainerWorkloadResponse;
import com.epam.trainerworkload.entity.MonthSummary;
import com.epam.trainerworkload.entity.TrainerWorkload;
import com.epam.trainerworkload.entity.YearSummary;
import com.epam.trainerworkload.exception.TrainerWorkloadNotFoundException;
import com.epam.trainerworkload.repository.TrainerWorkloadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainerWorkloadServiceTest {

    @Mock
    private TrainerWorkloadRepository trainerWorkloadRepository;

    @Mock
    private TrainerWorkloadTransactionExecutor transactionExecutor;

    @Mock
    private WorkloadUpdateLockManager lockManager;

    private TrainerWorkloadService service;

    @BeforeEach
    void setUp() {
        service = new TrainerWorkloadService(
                trainerWorkloadRepository,
                transactionExecutor,
                lockManager
        );
    }

    @Test
    void shouldReturnExistingMonthlyDuration() {
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.of(trainer()));

        MonthlyWorkloadResponse response = service.getMonthlyWorkload(
                "john.smith",
                2026,
                7
        );

        assertEquals("john.smith", response.trainerUsername());
        assertEquals(90, response.trainingSummaryDuration());
    }

    @Test
    void shouldReturnZeroWhenRequestedMonthDoesNotExist() {
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.of(trainer()));

        MonthlyWorkloadResponse response = service.getMonthlyWorkload(
                "john.smith",
                2026,
                9
        );

        assertEquals(0, response.trainingSummaryDuration());
    }

    @Test
    void shouldReturnAllEmbeddedYearsAndMonths() {
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.of(trainer()));

        TrainerWorkloadResponse response = service.getTrainerWorkload(
                "john.smith",
                null,
                null
        );

        assertEquals(2, response.years().size());
        assertEquals(2026, response.years().get(0).year());
        assertEquals(2, response.years().get(0).months().size());
        assertEquals(90, response.years().get(0).months().get(0)
                .trainingSummaryDuration());
        assertEquals(2027, response.years().get(1).year());
    }

    @Test
    void shouldReturnOnlyTheRequestedYearAndMonth() {
        when(trainerWorkloadRepository.findByUsername("john.smith"))
                .thenReturn(Optional.of(trainer()));

        TrainerWorkloadResponse response = service.getTrainerWorkload(
                "john.smith",
                2026,
                8
        );

        assertEquals(1, response.years().size());
        assertEquals(2026, response.years().get(0).year());
        assertEquals(1, response.years().get(0).months().size());
        assertEquals(8, response.years().get(0).months().get(0).month());
        assertEquals(30, response.years().get(0).months().get(0)
                .trainingSummaryDuration());
    }

    @Test
    void shouldRejectOnlyOneDateFilter() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.getTrainerWorkload("john.smith", 2026, null)
        );

        assertEquals(
                "Year and month must be provided together",
                exception.getMessage()
        );
        verifyNoInteractions(trainerWorkloadRepository);
    }

    @Test
    void shouldRejectUnknownTrainer() {
        when(trainerWorkloadRepository.findByUsername("unknown"))
                .thenReturn(Optional.empty());

        assertThrows(
                TrainerWorkloadNotFoundException.class,
                () -> service.getMonthlyWorkload("unknown", 2026, 7)
        );
    }

    private TrainerWorkload trainer() {
        TrainerWorkload trainer = new TrainerWorkload();
        trainer.setUsername("john.smith");
        trainer.setFirstName("John");
        trainer.setLastName("Smith");
        trainer.setActive(true);
        trainer.setYears(new ArrayList<>(List.of(
                new YearSummary(
                        2026,
                        new ArrayList<>(List.of(
                                new MonthSummary(7, 90),
                                new MonthSummary(8, 30)
                        ))
                ),
                new YearSummary(
                        2027,
                        new ArrayList<>(List.of(
                                new MonthSummary(1, 45)
                        ))
                )
        )));
        return trainer;
    }
}
