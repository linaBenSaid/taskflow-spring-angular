package com.taskflow.repository;

import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

	// Membership check used by every project and task operation.
	boolean existsByProjectIdAndUserId(Long projectId, Long userId);

	// Members of a project with their user loaded, oldest first (for the member list and the assignee picker).
	@EntityGraph(attributePaths = "user")
	List<ProjectMember> findByProjectIdOrderByJoinedAtAsc(Long projectId);
}
