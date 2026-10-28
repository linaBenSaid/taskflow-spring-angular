package com.taskflow.controller;

import com.taskflow.dto.CreateTaskRequest;
import com.taskflow.dto.TaskFilter;
import com.taskflow.dto.TaskResponse;
import com.taskflow.dto.UpdateTaskRequest;
import com.taskflow.entity.TaskPriority;
import com.taskflow.entity.TaskStatus;
import com.taskflow.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

// Tasks are listed and created under their project, and read/changed/deleted by their own id.
// HTTP only: all rules live in TaskService.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Tasks on a project's Kanban board. Only project members can see or change them.")
public class TaskController {

	private final TaskService taskService;

	// GET /api/projects/5/tasks?status=TODO&priority=HIGH&assignee=3&q=login&page=0&size=20&sort=dueDate,asc
	// All filters are optional. An unknown status/priority value is answered with 400.
	@GetMapping("/projects/{projectId}/tasks")
	@Operation(summary = "List a project's tasks", description = "All filters are optional and combine with AND. `q` searches titles (case-insensitive).")
	public PagedModel<TaskResponse> list(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long projectId,
			@RequestParam(required = false) TaskStatus status,
			@RequestParam(required = false) TaskPriority priority,
			@Parameter(description = "User id of the assignee") @RequestParam(name = "assignee", required = false) Long assigneeId,
			@Parameter(description = "Text to search for in task titles") @RequestParam(name = "q", required = false) String search,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		TaskFilter filter = new TaskFilter(status, priority, assigneeId, search);
		return new PagedModel<>(taskService.list(projectId, filter, pageable, principal.getUsername()));
	}

	// 201 Created, with a Location header pointing at /api/tasks/{id}.
	@PostMapping("/projects/{projectId}/tasks")
	@Operation(summary = "Create a task", description = "Only the title is required. Defaults: status TODO, priority MEDIUM, unassigned.")
	public ResponseEntity<TaskResponse> create(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long projectId,
			@Valid @RequestBody CreateTaskRequest request) {
		TaskResponse created = taskService.create(projectId, request, principal.getUsername());
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/tasks/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping("/tasks/{id}")
	@Operation(summary = "Get a task")
	public TaskResponse get(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
		return taskService.get(id, principal.getUsername());
	}

	// Partial update: only the fields in the body change (see UpdateTaskRequest).
	// The Kanban board moves a card with: PATCH /api/tasks/{id}  {"status": "IN_PROGRESS"}
	@PatchMapping("/tasks/{id}")
	@Operation(summary = "Update a task (partial)", description = """
			Only the fields sent change. Send `null` to clear `description`, `dueDate` or `assigneeId`.
			Moving a card on the board: `{"status": "IN_PROGRESS"}`.""")
	public TaskResponse update(
			@AuthenticationPrincipal UserDetails principal,
			@PathVariable Long id,
			@Valid @RequestBody UpdateTaskRequest request) {
		return taskService.update(id, request, principal.getUsername());
	}

	@DeleteMapping("/tasks/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a task")
	public void delete(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
		taskService.delete(id, principal.getUsername());
	}
}
