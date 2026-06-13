package com.taskmanager.service;

import com.taskmanager.dto.response.BurndownDataResponse;
import com.taskmanager.entity.*;
import com.taskmanager.enums.*;
import com.taskmanager.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BurndownServiceTest {

    @Mock SprintRepository sprintRepository;
    @Mock TaskRepository taskRepository;
    @Mock ProjectService projectService;
    @InjectMocks BurndownService burndownService;

    @Test
    void getBurndown_3daySprintWith2Tasks_idealLineIsCorrect() {
        Project proj = Project.builder().id(1L).build();
        Sprint sprint = Sprint.builder().id(1L).project(proj)
            .name("Sprint 1")
            .startDate(LocalDate.of(2026, 6, 1))
            .endDate(LocalDate.of(2026, 6, 3))
            .status(SprintStatus.ACTIVE).build();

        Task t1 = Task.builder().id(1L).status(TaskStatus.DONE).storyPoints(3)
            .updatedAt(LocalDateTime.of(2026, 6, 2, 10, 0)).build();
        Task t2 = Task.builder().id(2L).status(TaskStatus.TODO).storyPoints(2)
            .updatedAt(LocalDateTime.of(2026, 6, 1, 9, 0)).build();

        when(sprintRepository.findById(1L)).thenReturn(Optional.of(sprint));
        when(taskRepository.findBySprintId(1L)).thenReturn(List.of(t1, t2));
        doNothing().when(projectService).requireMember(anyLong(), anyLong());

        BurndownDataResponse result = burndownService.getBurndownData(1L, 99L);

        assertThat(result.byTask()).hasSize(3);
        // ideal for 3 days: day0=2.0, day1=1.0, day2=0.0
        assertThat(result.byTask().get(0).ideal()).isCloseTo(2.0, within(0.01));
        assertThat(result.byTask().get(2).ideal()).isCloseTo(0.0, within(0.01));
    }
}
