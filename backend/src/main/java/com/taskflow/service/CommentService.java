package com.taskflow.service;

import com.taskflow.dto.CommentResponse;
import com.taskflow.dto.CreateCommentRequest;
import com.taskflow.entity.Comment;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.mapper.CommentMapper;
import com.taskflow.repository.CommentRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Access rule: only members of the task's project can read or add comments.
// For everyone else the task doesn't exist (404), same as TaskService.
@Service
@RequiredArgsConstructor
public class CommentService {

	private final CommentRepository commentRepository;
	private final TaskRepository taskRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final UserRepository userRepository;
	private final CommentMapper commentMapper;

	@Transactional(readOnly = true)
	public Page<CommentResponse> list(Long taskId, Pageable pageable, String actorEmail) {
		findTaskForMember(taskId, loadActor(actorEmail));
		return commentRepository.findByTaskId(taskId, pageable).map(commentMapper::toResponse);
	}

	@Transactional
	public CommentResponse add(Long taskId, CreateCommentRequest request, String actorEmail) {
		User actor = loadActor(actorEmail);

		Comment comment = new Comment();
		comment.setTask(findTaskForMember(taskId, actor));
		comment.setAuthor(actor);
		comment.setBody(request.body().trim());

		return commentMapper.toResponse(commentRepository.save(comment));
	}

	private User loadActor(String email) {
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
	}

	private Task findTaskForMember(Long taskId, User actor) {
		// getProject().getId() doesn't load the project: Hibernate already knows the id.
		return taskRepository.findById(taskId)
				.filter(task -> projectMemberRepository.existsByProjectIdAndUserId(task.getProject().getId(), actor.getId()))
				.orElseThrow(() -> ResourceNotFoundException.task(taskId));
	}
}
