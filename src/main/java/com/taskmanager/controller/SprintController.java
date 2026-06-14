package com.taskmanager.controller;

import com.taskmanager.dto.request.CreateSprintRequest;
import com.taskmanager.dto.request.UpdateSprintRequest;
import com.taskmanager.dto.response.*;
import com.taskmanager.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;
    private final BurndownService burndownService;

    @GetMapping("/api/projects/{projectId}/sprints")
    public List<SprintResponse> getSprints(@PathVariable Long projectId,
            @AuthenticationPrincipal Long userId) {
        return sprintService.getSprints(projectId, userId);
    }

    @PostMapping("/api/projects/{projectId}/sprints")
    public ResponseEntity<SprintResponse> createSprint(@PathVariable Long projectId,
            @Valid @RequestBody CreateSprintRequest req,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(sprintService.createSprint(projectId, req, userId));
    }

    @PutMapping("/api/sprints/{id}/start")
    public SprintResponse startSprint(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return sprintService.startSprint(id, userId);
    }

    @PutMapping("/api/sprints/{id}/complete")
    public SprintResponse completeSprint(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return sprintService.completeSprint(id, userId);
    }

    @PutMapping("/api/sprints/{id}")
    public SprintResponse updateSprint(@PathVariable Long id,
            @Valid @RequestBody UpdateSprintRequest req,
            @AuthenticationPrincipal Long userId) {
        return sprintService.updateSprint(id, req, userId);
    }

    @GetMapping("/api/sprints/{id}/burndown")
    public BurndownDataResponse getBurndown(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return burndownService.getBurndownData(id, userId);
    }
}
