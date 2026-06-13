package com.taskmanager.service;

import com.taskmanager.dto.request.*;
import com.taskmanager.dto.response.*;
import com.taskmanager.entity.*;
import com.taskmanager.enums.*;
import com.taskmanager.exception.*;
import com.taskmanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectService projectService;

    public List<TaskResponse> getTasksByProject(Long projectId, Long sprintId, Long userId) {
        projectService.requireMember(projectId, userId);
        List<Task> tasks = sprintId != null
            ? taskRepository.findByProjectIdAndSprintId(projectId, sprintId)
            : taskRepository.findByProjectId(projectId);
        return tasks.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TaskResponse createTask(Long projectId, CreateTaskRequest req, Long userId) {
        projectService.requireRole(projectId, userId, ProjectRole.MANAGER);
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Dự án không tồn tại"));
        User creator = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));

        Task task = Task.builder()
            .project(project)
            .title(req.title())
            .description(req.description())
            .status(req.status() != null ? req.status() : TaskStatus.TODO)
            .priority(req.priority() != null ? req.priority() : Priority.MEDIUM)
            .storyPoints(req.storyPoints())
            .dueDate(req.dueDate())
            .createdBy(creator)
            .build();

        if (req.assigneeId() != null) {
            task.setAssignee(userRepository.findById(req.assigneeId()).orElse(null));
        }
        if (req.sprintId() != null) {
            task.setSprint(sprintRepository.findById(req.sprintId()).orElse(null));
        }

        return toResponse(taskRepository.save(task));
    }

    public TaskResponse getTask(Long taskId, Long userId) {
        Task task = getTaskEntity(taskId);
        projectService.requireMember(task.getProject().getId(), userId);
        return toResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(Long taskId, UpdateTaskRequest req, Long userId) {
        Task task = getTaskEntity(taskId);
        projectService.requireRole(task.getProject().getId(), userId, ProjectRole.MANAGER);

        if (req.title() != null) task.setTitle(req.title());
        if (req.description() != null) task.setDescription(req.description());
        if (req.status() != null) task.setStatus(req.status());
        if (req.priority() != null) task.setPriority(req.priority());
        if (req.storyPoints() != null) task.setStoryPoints(req.storyPoints());
        if (req.dueDate() != null) task.setDueDate(req.dueDate());
        if (req.assigneeId() != null) {
            task.setAssignee(userRepository.findById(req.assigneeId()).orElse(null));
        }
        if (req.sprintId() != null) {
            task.setSprint(sprintRepository.findById(req.sprintId()).orElse(null));
        }

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateTaskStatus(Long taskId, UpdateTaskStatusRequest req, Long userId) {
        Task task = getTaskEntity(taskId);
        Long projectId = task.getProject().getId();

        Optional<ProjectMember> pm = memberRepository.findByProjectIdAndUserId(projectId, userId);
        if (pm.isEmpty()) throw new AccessDeniedException("Bạn không phải thành viên của dự án");

        ProjectRole role = pm.get().getRole();
        boolean isManagerOrAdmin = role == ProjectRole.ADMIN || role == ProjectRole.MANAGER;
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(userId);

        if (!isManagerOrAdmin && !isAssignee) {
            throw new AccessDeniedException("Chỉ assignee hoặc Manager/Admin mới có thể cập nhật trạng thái");
        }

        task.setStatus(req.status());
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        Task task = getTaskEntity(taskId);
        projectService.requireRole(task.getProject().getId(), userId, ProjectRole.MANAGER);
        taskRepository.deleteById(taskId);
    }

    public List<TaskResponse> getMyTasks(Long userId, TaskStatus status) {
        List<Task> tasks = status != null
            ? taskRepository.findByAssigneeIdAndStatus(userId, status)
            : taskRepository.findByAssigneeId(userId);
        return tasks.stream().map(this::toResponse).toList();
    }

    private Task getTaskEntity(Long id) {
        return taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task không tồn tại"));
    }

    public TaskResponse toResponse(Task t) {
        return new TaskResponse(
            t.getId(), t.getProject().getId(), t.getProject().getName(),
            t.getSprint() != null ? t.getSprint().getId() : null,
            t.getSprint() != null ? t.getSprint().getName() : null,
            t.getTitle(), t.getDescription(),
            t.getStatus(), t.getPriority(), t.getStoryPoints(),
            t.getAssignee() != null ? toSummary(t.getAssignee()) : null,
            t.getCreatedBy() != null ? toSummary(t.getCreatedBy()) : null,
            t.getDueDate(), t.getCreatedAt(), t.getUpdatedAt()
        );
    }

    private UserSummary toSummary(User u) {
        return new UserSummary(u.getId(), u.getUsername(), u.getEmail(), u.getAvatarUrl());
    }
}
