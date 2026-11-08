package com.taskflow.controller;

import com.taskflow.dto.CommentResponse;
import com.taskflow.dto.CreateCommentRequest;
import com.taskflow.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// HTTP only: all rules live in CommentService.
@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Comments on a task. Only project members can read or add them.")
public class CommentController {

	private final CommentService commentService;

	// Oldest first, like a conversation. id breaks the tie between comments created at the same instant.
	@GetMapping
	@Operation(summary = "List a task's comments", description = "Oldest first.")
	public PagedModel<CommentResponse> list(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long taskId,
			@ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
		return new PagedModel<>(commentService.list(taskId, pageable, principal.getUsername()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add a comment", description = "The logged-in user becomes its author.")
	public CommentResponse add(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long taskId,
			@Valid @RequestBody CreateCommentRequest request) {
		return commentService.add(taskId, request, principal.getUsername());
	}
}
