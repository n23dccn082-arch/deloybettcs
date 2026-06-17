package com.taskmanager.service;

import com.taskmanager.dto.response.*;
import com.taskmanager.entity.*;
import com.taskmanager.enums.TaskStatus;
import com.taskmanager.exception.ResourceNotFoundException;
import com.taskmanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BurndownService {

    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final ProjectService projectService;

    public BurndownDataResponse getBurndownData(Long sprintId, Long userId) {
        Sprint sprint = sprintRepository.findById(sprintId)
            .orElseThrow(() -> new ResourceNotFoundException("Sprint không tồn tại"));
        projectService.requireMember(sprint.getProject().getId(), userId);

        List<Task> tasks = taskRepository.findBySprintId(sprintId);
        LocalDate start = sprint.getStartDate();
        LocalDate end = sprint.getEndDate();

        if (start == null || end == null || tasks.isEmpty()) {
            return new BurndownDataResponse(sprintId, sprint.getName(), List.of(), List.of(), false, 0, 0);
        }

        List<BurndownPoint> byTask = computeBurndown(tasks, start, end, false);
        List<BurndownPoint> byPoint = computeBurndown(tasks, start, end, true);
        boolean sprintGoalMissed = isSprintGoalMissed(byTask, tasks, end);
        int overdueTasksNow = countOverdueTasks(tasks);
        int overduePointsNow = sumOverduePoints(tasks);

        return new BurndownDataResponse(
            sprintId, sprint.getName(), byTask, byPoint, sprintGoalMissed, overdueTasksNow, overduePointsNow);
    }

    private List<BurndownPoint> computeBurndown(List<Task> tasks,
                                                 LocalDate start, LocalDate end,
                                                 boolean usePoints) {
        long totalDays = start.until(end).getDays() + 1;
        double total = usePoints
            ? tasks.stream().mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum()
            : tasks.size();

        LocalDate today = LocalDate.now();
        List<BurndownPoint> points = new ArrayList<>();

        for (long i = 0; i < totalDays; i++) {
            LocalDate day = start.plusDays(i);
            double divisor = totalDays <= 1 ? 1.0 : (totalDays - 1);
            double ideal = total * (1.0 - (double) i / divisor);
            if (i == totalDays - 1) ideal = 0.0;

            Double actual = null;
            double overdue = 0;
            if (!day.isAfter(today)) {
                LocalDateTime endOfDay = day.plusDays(1).atStartOfDay();
                double remaining = tasks.stream()
                    .filter(t -> !isDoneByEndOfDay(t, endOfDay))
                    .mapToDouble(t -> usePoints ? (t.getStoryPoints() != null ? t.getStoryPoints() : 0) : 1)
                    .sum();
                actual = remaining;
                overdue = tasks.stream()
                    .filter(t -> !isDoneByEndOfDay(t, endOfDay))
                    .filter(t -> isOverdueOnDay(t, day))
                    .mapToDouble(t -> usePoints ? (t.getStoryPoints() != null ? t.getStoryPoints() : 0) : 1)
                    .sum();
            }

            points.add(new BurndownPoint(day.toString(), ideal, actual, overdue, day.equals(end)));
        }
        return points;
    }

    private boolean isSprintGoalMissed(List<BurndownPoint> byTask, List<Task> tasks, LocalDate end) {
        LocalDate today = LocalDate.now();
        Optional<BurndownPoint> endPoint = byTask.stream().filter(BurndownPoint::sprintEnd).findFirst();
        if (endPoint.isPresent() && endPoint.get().actual() != null) {
            return endPoint.get().actual() > 0;
        }
        if (!today.isAfter(end)) {
            return false;
        }
        return tasks.stream().anyMatch(t -> t.getStatus() != TaskStatus.DONE);
    }

    private int countOverdueTasks(List<Task> tasks) {
        LocalDate today = LocalDate.now();
        return (int) tasks.stream()
            .filter(t -> t.getStatus() != TaskStatus.DONE)
            .filter(t -> isOverdueOnDay(t, today))
            .count();
    }

    private int sumOverduePoints(List<Task> tasks) {
        LocalDate today = LocalDate.now();
        return tasks.stream()
            .filter(t -> t.getStatus() != TaskStatus.DONE)
            .filter(t -> isOverdueOnDay(t, today))
            .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0)
            .sum();
    }

    private boolean isOverdueOnDay(Task task, LocalDate day) {
        return task.getDueDate() != null && task.getDueDate().isBefore(day);
    }

    private boolean isDoneByEndOfDay(Task task, LocalDateTime endOfDay) {
        if (task.getStatus() != TaskStatus.DONE) return false;
        return task.getUpdatedAt() != null && task.getUpdatedAt().isBefore(endOfDay);
    }
}
