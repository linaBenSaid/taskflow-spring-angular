package com.taskflow.repository;

import com.taskflow.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// JpaSpecificationExecutor adds findAll(Specification, Pageable): queries built from TaskSpecifications.
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

	// A task with its assignee, which every TaskResponse shows.
	@EntityGraph(attributePaths = "assignee")
	Optional<Task> findWithAssigneeById(Long id);

	// Same as the inherited method, plus @EntityGraph: a page of 20 tasks loads their assignees
	// in the same query instead of 20 extra ones (N+1).
	@Override
	@EntityGraph(attributePaths = "assignee")
	Page<Task> findAll(Specification<Task> spec, Pageable pageable);

	// When someone leaves a project, their tasks there become unassigned.
	// One UPDATE statement instead of loading every task.
	// - flushAutomatically: write pending changes (e.g. the removed member row) to the database first.
	// - clearAutomatically: afterwards, empty Hibernate's in-memory cache, which still holds the old assignee
	//   for any task already loaded in this transaction. Otherwise a later read could return that stale copy.
	// Bulk updates skip @UpdateTimestamp, so updatedAt is set here ("instant" = now, in HQL).
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Task t set t.assignee = null, t.updatedAt = instant
			where t.project.id = :projectId and t.assignee.id = :userId""")
	int unassignAllInProject(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
