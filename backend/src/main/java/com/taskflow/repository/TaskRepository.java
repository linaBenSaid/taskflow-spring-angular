package com.taskflow.repository;

import com.taskflow.entity.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

	// A task with its assignee, which every TaskResponse shows.
	@EntityGraph(attributePaths = "assignee")
	Optional<Task> findWithAssigneeById(Long id);

	// When someone leaves a project, their tasks there become unassigned.
	// One UPDATE statement instead of loading every task. flushAutomatically writes pending changes first.
	// Bulk updates skip @UpdateTimestamp, so updatedAt is set here ("instant" = now, in HQL).
	@Modifying(flushAutomatically = true)
	@Query("""
			update Task t set t.assignee = null, t.updatedAt = instant
			where t.project.id = :projectId and t.assignee.id = :userId""")
	int unassignAllInProject(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
