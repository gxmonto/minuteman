package com.baseline.timetrack.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baseline.timetrack.entity.Task;
import com.baseline.timetrack.entity.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findBySourceAndExternalId(String source, String externalId);

    @EntityGraph(attributePaths = "site")
    @Query("""
            select t from Task t join t.assignees a
            where a.id = :userId and t.status in :statuses
            order by t.dueAt asc nulls last, t.id asc""")
    List<Task> findAssignedTo(@Param("userId") Long userId, @Param("statuses") Collection<TaskStatus> statuses);

    @Query("""
            select t from Task t join t.assignees a
            where a.id = :userId and t.source = :source""")
    List<Task> findAssignedFromSource(@Param("userId") Long userId, @Param("source") String source);
}
