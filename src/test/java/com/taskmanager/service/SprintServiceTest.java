package com.taskmanager.service;

import com.taskmanager.dto.request.UpdateSprintRequest;
import com.taskmanager.entity.*;
import com.taskmanager.enums.*;
import com.taskmanager.exception.*;
import com.taskmanager.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

    @Mock SprintRepository sprintRepository;
    @Mock ProjectRepository projectRepository;
    @Mock TaskRepository taskRepository;
    @Mock ProjectService projectService;
    @InjectMocks SprintService sprintService;

    @Test
    void updateSprint_completedSprint_throwsBadRequest() {
        Project proj = Project.builder().id(1L).build();
        Sprint sprint = Sprint.builder().id(5L).project(proj)
            .name("Sprint 1").status(SprintStatus.COMPLETED).build();

        when(sprintRepository.findById(5L)).thenReturn(Optional.of(sprint));
        doNothing().when(projectService).requireRole(1L, 10L, ProjectRole.MANAGER);

        UpdateSprintRequest req = new UpdateSprintRequest("New Name", null, null, null);
        assertThatThrownBy(() -> sprintService.updateSprint(5L, req, 10L))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("COMPLETED");
    }

    @Test
    void updateSprint_activeSprint_updatesNameAndDates() {
        Project proj = Project.builder().id(1L).build();
        Sprint sprint = Sprint.builder().id(5L).project(proj)
            .name("Sprint 1").status(SprintStatus.ACTIVE)
            .startDate(LocalDate.of(2026, 6, 1)).endDate(LocalDate.of(2026, 6, 14)).build();

        when(sprintRepository.findById(5L)).thenReturn(Optional.of(sprint));
        doNothing().when(projectService).requireRole(1L, 10L, ProjectRole.MANAGER);
        when(sprintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.countBySprintId(any())).thenReturn(0L);
        when(taskRepository.countDoneBySprintId(any())).thenReturn(0L);
        when(taskRepository.sumStoryPointsBySprintId(any())).thenReturn(0L);
        when(taskRepository.sumDoneStoryPointsBySprintId(any())).thenReturn(0L);

        UpdateSprintRequest req = new UpdateSprintRequest(
            "Sprint 1 - Fixed",
            "Hoàn thành auth",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 21)
        );
        var result = sprintService.updateSprint(5L, req, 10L);

        assertThat(result.name()).isEqualTo("Sprint 1 - Fixed");
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 6, 21));
        assertThat(result.goal()).isEqualTo("Hoàn thành auth");
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }
}
