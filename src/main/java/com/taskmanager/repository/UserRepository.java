package com.taskmanager.repository;

import com.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    @Modifying
    @Query("""
        DELETE FROM User u
        WHERE u.emailVerified = false
          AND u.createdAt < :cutoff
          AND NOT EXISTS (SELECT 1 FROM Project p WHERE p.createdBy = u)
          AND NOT EXISTS (SELECT 1 FROM ProjectMember pm WHERE pm.user = u)
          AND NOT EXISTS (SELECT 1 FROM Task t WHERE t.createdBy = u OR t.assignee = u)
          AND NOT EXISTS (SELECT 1 FROM Comment c WHERE c.user = u)
        """)
    int deleteOrphanUnverifiedCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
        SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END
        FROM User u WHERE u.id = :userId AND (
            EXISTS (SELECT 1 FROM Project p WHERE p.createdBy.id = :userId)
            OR EXISTS (SELECT 1 FROM ProjectMember pm WHERE pm.user.id = :userId)
            OR EXISTS (SELECT 1 FROM Task t WHERE t.createdBy.id = :userId OR t.assignee.id = :userId)
            OR EXISTS (SELECT 1 FROM Comment c WHERE c.user.id = :userId)
        )
        """)
    boolean hasUserDependencies(@Param("userId") Long userId);
}
