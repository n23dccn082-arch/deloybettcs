package com.taskmanager.repository;

import com.taskmanager.entity.Task;
import com.taskmanager.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectId(Long projectId);
    List<Task> findByProjectIdAndSprintId(Long projectId, Long sprintId);
    List<Task> findByProjectIdAndSprintIsNull(Long projectId);
    List<Task> findBySprintId(Long sprintId);
    List<Task> findByAssigneeId(Long assigneeId);
    List<Task> findByAssigneeIdAndStatus(Long assigneeId, TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.assignee.id = :userId AND t.dueDate = :today AND t.status != 'DONE'")
    List<Task> findTodayTasks(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Query("SELECT t FROM Task t WHERE t.assignee.id = :userId AND t.dueDate > :today AND t.dueDate <= :limit AND t.status != 'DONE' ORDER BY t.dueDate ASC")
    List<Task> findUpcomingTasks(@Param("userId") Long userId, @Param("today") LocalDate today, @Param("limit") LocalDate limit);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.sprint.id = :sprintId")
    long countBySprintId(@Param("sprintId") Long sprintId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.sprint.id = :sprintId AND t.status = 'DONE'")
    long countDoneBySprintId(@Param("sprintId") Long sprintId);

    @Query("SELECT COALESCE(SUM(t.storyPoints), 0) FROM Task t WHERE t.sprint.id = :sprintId")
    long sumStoryPointsBySprintId(@Param("sprintId") Long sprintId);

    @Query("SELECT COALESCE(SUM(t.storyPoints), 0) FROM Task t WHERE t.sprint.id = :sprintId AND t.status = 'DONE'")
    long sumDoneStoryPointsBySprintId(@Param("sprintId") Long sprintId);

    @Modifying
    @Query("UPDATE Task t SET t.sprint = NULL WHERE t.sprint.id = :sprintId")
    void clearSprintFromTasks(@Param("sprintId") Long sprintId);
}
