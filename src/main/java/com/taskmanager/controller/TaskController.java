package com.taskmanager.controller;

import com.taskmanager.dto.request.*;
import com.taskmanager.dto.response.TaskResponse;
import com.taskmanager.enums.TaskStatus;
import com.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/api/projects/{projectId}/tasks")
    public List<TaskResponse> getTasks(@PathVariable Long projectId,
            @RequestParam(required = false) Long sprintId,
            @AuthenticationPrincipal Long userId) {
        return taskService.getTasksByProject(projectId, sprintId, userId);
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> createTask(@PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest req,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(taskService.createTask(projectId, req, userId));
    }

    @GetMapping("/api/tasks/{id}")
    public TaskResponse getTask(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return taskService.getTask(id, userId);
    }

    @PutMapping("/api/tasks/{id}")
    public TaskResponse updateTask(@PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest req,
            @AuthenticationPrincipal Long userId) {
        return taskService.updateTask(id, req, userId);
    }

    @PutMapping("/api/tasks/{id}/status")
    public TaskResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateTaskStatusRequest req,
            @AuthenticationPrincipal Long userId) {
        return taskService.updateTaskStatus(id, req, userId);
    }

    @DeleteMapping("/api/tasks/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        taskService.deleteTask(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/tasks/my-tasks")
    public List<TaskResponse> getMyTasks(
            @RequestParam(required = false) TaskStatus status,
            @AuthenticationPrincipal Long userId) {
        return taskService.getMyTasks(userId, status);
    }
}
