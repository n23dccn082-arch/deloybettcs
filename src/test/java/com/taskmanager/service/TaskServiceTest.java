package com.taskmanager.service;

import com.taskmanager.dto.request.UpdateTaskStatusRequest;
import com.taskmanager.entity.*;
import com.taskmanager.enums.*;
import com.taskmanager.exception.AccessDeniedException;
import com.taskmanager.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock SprintRepository sprintRepository;
    @Mock UserRepository userRepository;
    @Mock ProjectMemberRepository memberRepository;
    @Mock ProjectService projectService;
    @InjectMocks TaskService taskService;

    @Test
    void updateStatus_memberNotAssignee_throwsAccessDenied() {
        User assignee = User.builder().id(99L).build();
        Project proj = Project.builder().id(1L).build();
        Task task = Task.builder().id(10L).project(proj).assignee(assignee).status(TaskStatus.TODO).build();

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(memberRepository.findByProjectIdAndUserId(1L, 2L))
            .thenReturn(Optional.of(ProjectMember.builder().role(ProjectRole.MEMBER).build()));

        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS);
        assertThatThrownBy(() -> taskService.updateTaskStatus(10L, req, 2L))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateStatus_assignee_succeeds() {
        User assignee = User.builder().id(2L).build();
        Project proj = Project.builder().id(1L).build();
        Task task = Task.builder().id(10L).project(proj).assignee(assignee)
            .status(TaskStatus.TODO).build();

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(memberRepository.findByProjectIdAndUserId(1L, 2L))
            .thenReturn(Optional.of(ProjectMember.builder().role(ProjectRole.MEMBER).build()));
        when(taskRepository.save(any())).thenReturn(task);

        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS);
        taskService.updateTaskStatus(10L, req, 2L);

        verify(taskRepository).save(argThat(t -> t.getStatus() == TaskStatus.IN_PROGRESS));
    }
}
