package com.taskflow.service;

import com.taskflow.dto.AddMemberRequest;
import com.taskflow.dto.ProjectMemberResponse;
import com.taskflow.dto.ProjectRequest;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.exception.ConflictException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.util.Emails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Access rules:
// - Only members can see a project. For everyone else it doesn't exist (404), so project ids can't be probed.
// - Only the owner can edit a project or add members.
// - The owner or an ADMIN can delete a project or remove members. A member can always leave (remove themselves).
// - The owner can't be removed from their own project.
// - Whoever leaves or is removed gets unassigned from their tasks in that project.
@Service
@RequiredArgsConstructor
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final UserRepository userRepository;
	private final TaskRepository taskRepository;

	@Transactional
	public ProjectResponse create(ProjectRequest request, String actorEmail) {
		User actor = loadActor(actorEmail);

		Project project = new Project();
		applyChanges(project, request);
		project.setOwner(actor);
		project.addMember(actor); // the owner is always a member too

		return ProjectResponse.from(projectRepository.save(project));
	}

	@Transactional(readOnly = true)
	public Page<ProjectResponse> listMine(String actorEmail, Pageable pageable) {
		User actor = loadActor(actorEmail);
		return projectRepository.findByMembersUserId(actor.getId(), pageable).map(ProjectResponse::from);
	}

	@Transactional(readOnly = true)
	public ProjectResponse get(Long projectId, String actorEmail) {
		return ProjectResponse.from(findForMember(projectId, loadActor(actorEmail)));
	}

	@Transactional
	public ProjectResponse update(Long projectId, ProjectRequest request, String actorEmail) {
		User actor = loadActor(actorEmail);
		Project project = findForMember(projectId, actor);
		if (!isOwner(project, actor)) {
			throw new AccessDeniedException("Only the project owner can edit it");
		}

		applyChanges(project, request);
		return ProjectResponse.from(project); // saved automatically when the transaction commits
	}

	@Transactional
	public void delete(Long projectId, String actorEmail) {
		User actor = loadActor(actorEmail);
		Project project = findForMemberOrAdmin(projectId, actor);
		if (!isOwner(project, actor) && !isAdmin(actor)) {
			throw new AccessDeniedException("Only the project owner or an admin can delete it");
		}

		projectRepository.delete(project);
	}

	@Transactional(readOnly = true)
	public List<ProjectMemberResponse> listMembers(Long projectId, String actorEmail) {
		User actor = loadActor(actorEmail);
		if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, actor.getId())) {
			throw ResourceNotFoundException.project(projectId);
		}

		return projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(projectId).stream()
				.map(ProjectMemberResponse::from)
				.toList();
	}

	@Transactional
	public ProjectMemberResponse addMember(Long projectId, AddMemberRequest request, String actorEmail) {
		User actor = loadActor(actorEmail);
		Project project = findForMember(projectId, actor);
		if (!isOwner(project, actor)) {
			throw new AccessDeniedException("Only the project owner can add members");
		}

		String email = Emails.normalize(request.email());
		User newMember = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("No user with email " + email));
		if (projectMemberRepository.existsByProjectIdAndUserId(projectId, newMember.getId())) {
			throw new ConflictException(email + " is already a member of this project");
		}

		ProjectMember member = project.addMember(newMember);
		projectRepository.flush(); // insert now, so joinedAt is filled in for the response
		return ProjectMemberResponse.from(member);
	}

	@Transactional
	public void removeMember(Long projectId, Long userId, String actorEmail) {
		User actor = loadActor(actorEmail);
		Project project = findForMemberOrAdmin(projectId, actor);

		boolean leaving = actor.getId().equals(userId);
		if (!leaving && !isOwner(project, actor) && !isAdmin(actor)) {
			throw new AccessDeniedException("Only the project owner or an admin can remove members");
		}
		if (project.getOwner().getId().equals(userId)) {
			throw new ConflictException("The owner can't be removed from their own project");
		}
		if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
			throw new ResourceNotFoundException("User " + userId + " is not a member of this project");
		}

		// Their tasks stay on the board, unassigned: nobody should be assigned work in a project they can't see.
		taskRepository.unassignAllInProject(projectId, userId);
		project.removeMember(userId); // orphanRemoval deletes the row when the transaction commits
	}

	private User loadActor(String email) {
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
	}

	private Project findForMember(Long projectId, User actor) {
		return projectRepository.findByIdAndMembersUserId(projectId, actor.getId())
				.orElseThrow(() -> ResourceNotFoundException.project(projectId));
	}

	// Admins can act on any project, even ones they aren't a member of.
	private Project findForMemberOrAdmin(Long projectId, User actor) {
		if (isAdmin(actor)) {
			return projectRepository.findById(projectId)
					.orElseThrow(() -> ResourceNotFoundException.project(projectId));
		}
		return findForMember(projectId, actor);
	}

	private static boolean isOwner(Project project, User user) {
		// getId() on a lazy owner doesn't hit the database: Hibernate already knows the id.
		return project.getOwner().getId().equals(user.getId());
	}

	private static boolean isAdmin(User user) {
		return user.getRole() == Role.ADMIN;
	}

	private static void applyChanges(Project project, ProjectRequest request) {
		project.setName(request.name().trim());
		project.setDescription(request.description() == null || request.description().isBlank()
				? null
				: request.description().trim());
	}
}
