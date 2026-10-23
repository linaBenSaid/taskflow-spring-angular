package com.taskflow.repository;

import com.taskflow.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	// "MembersUserId" walks Project.members -> ProjectMember.user -> User.id:
	// the projects the given user is a member of, one page at a time.
	// @EntityGraph loads each project's owner in the same query, instead of one extra query per project (N+1).
	@EntityGraph(attributePaths = "owner")
	Page<Project> findByMembersUserId(Long userId, Pageable pageable);

	// A project, but only if the user is a member. Empty for non-members, so the API can answer 404
	// without revealing that the project exists.
	@EntityGraph(attributePaths = "owner")
	Optional<Project> findByIdAndMembersUserId(Long id, Long userId);
}
