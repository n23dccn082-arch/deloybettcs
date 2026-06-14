package com.taskmanager.service;

import com.taskmanager.dto.request.CreateSprintRequest;
import com.taskmanager.dto.request.UpdateSprintRequest;
import com.taskmanager.dto.response.SprintResponse;
import com.taskmanager.entity.*;
import com.taskmanager.enums.ProjectRole;
import com.taskmanager.enums.SprintStatus;
import com.taskmanager.exception.*;
import com.taskmanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ProjectService projectService;

    public List<SprintResponse> getSprints(Long projectId, Long userId) {
        projectService.requireMember(projectId, userId);
        return sprintRepository.findByProjectIdOrderByIdDesc(projectId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public SprintResponse createSprint(Long projectId, CreateSprintRequest req, Long userId) {
        projectService.requireRole(projectId, userId, ProjectRole.MANAGER);
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Dự án không tồn tại"));
        Sprint sprint = Sprint.builder()
            .project(project).name(req.name()).goal(req.goal())
            .startDate(req.startDate()).endDate(req.endDate()).build();
        return toResponse(sprintRepository.save(sprint));
    }

    @Transactional
    public SprintResponse startSprint(Long sprintId, Long userId) {
        Sprint sprint = getSprint(sprintId);
        projectService.requireRole(sprint.getProject().getId(), userId, ProjectRole.MANAGER);
        if (sprintRepository.existsByProjectIdAndStatus(sprint.getProject().getId(), SprintStatus.ACTIVE)) {
            throw new ConflictException("Dự án đã có sprint đang chạy, hãy kết thúc trước");
        }
        if (sprint.getStatus() != SprintStatus.PLANNING) {
            throw new BadRequestException("Chỉ sprint ở trạng thái PLANNING mới có thể bắt đầu");
        }
        sprint.setStatus(SprintStatus.ACTIVE);
        return toResponse(sprintRepository.save(sprint));
    }

    @Transactional
    public SprintResponse completeSprint(Long sprintId, Long userId) {
        Sprint sprint = getSprint(sprintId);
        projectService.requireRole(sprint.getProject().getId(), userId, ProjectRole.MANAGER);
        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new BadRequestException("Chỉ sprint đang ACTIVE mới có thể kết thúc");
        }
        sprint.setStatus(SprintStatus.COMPLETED);
        return toResponse(sprintRepository.save(sprint));
    }

    @Transactional
    public SprintResponse updateSprint(Long sprintId, UpdateSprintRequest req, Long userId) {
        Sprint sprint = getSprint(sprintId);
        projectService.requireRole(sprint.getProject().getId(), userId, ProjectRole.MANAGER);
        if (sprint.getStatus() == SprintStatus.COMPLETED) {
            throw new BadRequestException("Không thể sửa sprint đã COMPLETED");
        }
        if (req.name() != null && !req.name().isBlank()) sprint.setName(req.name());
        if (req.goal() != null) sprint.setGoal(req.goal());
        if (req.startDate() != null) sprint.setStartDate(req.startDate());
        if (req.endDate() != null) sprint.setEndDate(req.endDate());
        return toResponse(sprintRepository.save(sprint));
    }

    public SprintResponse getSprintById(Long sprintId, Long userId) {
        Sprint sprint = getSprint(sprintId);
        projectService.requireMember(sprint.getProject().getId(), userId);
        return toResponse(sprint);
    }

    private Sprint getSprint(Long id) {
        return sprintRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Sprint không tồn tại"));
    }

    SprintResponse toResponse(Sprint s) {
        long total = taskRepository.countBySprintId(s.getId());
        long done = taskRepository.countDoneBySprintId(s.getId());
        long totalPts = taskRepository.sumStoryPointsBySprintId(s.getId());
        long donePts = taskRepository.sumDoneStoryPointsBySprintId(s.getId());
        return new SprintResponse(s.getId(), s.getProject().getId(), s.getName(), s.getGoal(),
            s.getStartDate(), s.getEndDate(), s.getStatus(), total, done, totalPts, donePts);
    }
}
