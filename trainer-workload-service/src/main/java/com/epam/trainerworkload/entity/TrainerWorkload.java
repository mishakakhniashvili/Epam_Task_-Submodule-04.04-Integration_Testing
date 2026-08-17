package com.epam.trainerworkload.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor

@CompoundIndex(
        name = "trainer_name_idx",
        def = "{'firstName': 1, 'lastName': 1}"
)
@Document(collection = "trainer_workloads")
public class TrainerWorkload {

    private List<YearSummary> years = new ArrayList<>();

    @Id
    private String id;

    @Indexed(unique = true)
    @NotBlank
    private String username ;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName ;

    private boolean active;

}
