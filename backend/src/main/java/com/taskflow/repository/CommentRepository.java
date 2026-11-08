package com.taskflow.repository;

import com.taskflow.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	// One page of a task's comments, with each author loaded in the same query (no N+1).
	@EntityGraph(attributePaths = "author")
	Page<Comment> findByTaskId(Long taskId, Pageable pageable);
}
