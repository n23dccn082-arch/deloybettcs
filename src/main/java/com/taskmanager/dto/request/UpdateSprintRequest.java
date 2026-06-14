package com.taskmanager.dto.request;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateSprintRequest(
    @Size(max = 100) String name,
    String goal,
    LocalDate startDate,
    LocalDate endDate
) {}
