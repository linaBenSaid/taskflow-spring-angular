package com.taskflow.service;

import com.taskflow.dto.CreateTaskRequest;
import com.taskflow.dto.TaskResponse;
import com.taskflow.dto.UpdateTaskRequest;
import com.taskflow.entity.Project;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.exception.InvalidRequestException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Access rules:
// - Every task operation requires being a member of the task's project.
//   For non-members the task (or project) doesn't exist: 404, same as ProjectService.
// - Any member can create, edit, move and delete tasks (the board is shared).
// - The assignee must be a member of the project (400 otherwise).
@Service
@RequiredArgsConstructor
public class TaskService {

	private final TaskRepository taskRepository;
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final UserRepository userRepository;

	@Transactional
	public TaskResponse create(Long projectId, CreateTaskRequest request, String actorEmail) {
		User actor = loadActor(actorEmail);
		Project project = projectRepository.findByIdAndMembersUserId(projectId, actor.getId())
				.orElseThrow(() -> ResourceNotFoundException.project(projectId));

		Task task = new Task();
		task.setProject(project);
		task.setTitle(request.title().trim());
		task.setDescription(blankToNull(request.description()));
		if (request.status() != null) {
			task.setStatus(request.status());
		}
		if (request.priority() != null) {
			task.setPriority(request.priority());
		}
		task.setDueDate(request.dueDate());
		task.setAssignee(request.assigneeId() == null ? null : findAssignee(projectId, request.assigneeId()));

		return TaskResponse.from(taskRepository.save(task));
	}

	@Transactional(readOnly = true)
	public TaskResponse get(Long taskId, String actorEmail) {
		return TaskResponse.from(findForMember(taskId, loadActor(actorEmail)));
	}

	// Partial update: null = not sent (keep), Optional.empty() = clear, Optional.of(x) = set. See UpdateTaskRequest.
	@Transactional
	public TaskResponse update(Long taskId, UpdateTaskRequest request, String actorEmail) {
		Task task = findForMember(taskId, loadActor(actorEmail));

		if (request.getTitle() != null) {
			task.setTitle(request.getTitle().trim());
		}
		if (request.getDescription() != null) {
			task.setDescription(blankToNull(request.getDescription().orElse(null)));
		}
		if (request.getStatus() != null) {
			task.setStatus(request.getStatus());
		}
		if (request.getPriority() != null) {
			task.setPriority(request.getPriority());
		}
		if (request.getDueDate() != null) {
			task.setDueDate(request.getDueDate().orElse(null));
		}
		if (request.getAssigneeId() != null) {
			Long projectId = task.getProject().getId();
			task.setAssignee(request.getAssigneeId().map(userId -> findAssignee(projectId, userId)).orElse(null));
		}

		// flush so @UpdateTimestamp sets updatedAt before we build the response
		taskRepository.flush();
		return TaskResponse.from(task);
	}

	@Transactional
	public void delete(Long taskId, String actorEmail) {
		taskRepository.delete(findForMember(taskId, loadActor(actorEmail)));
	}

	private User loadActor(String email) {
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
	}

	private Task findForMember(Long taskId, User actor) {
		// getProject().getId() doesn't load the project: Hibernate already knows the id.
		return taskRepository.findWithAssigneeById(taskId)
				.filter(task -> projectMemberRepository.existsByProjectIdAndUserId(task.getProject().getId(), actor.getId()))
				.orElseThrow(() -> ResourceNotFoundException.task(taskId));
	}

	private User findAssignee(Long projectId, Long userId) {
		if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
			throw new InvalidRequestException("The assignee must be a member of the project");
		}
		return userRepository.findById(userId)
				.orElseThrow(() -> new InvalidRequestException("The assignee must be a member of the project"));
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}
}
