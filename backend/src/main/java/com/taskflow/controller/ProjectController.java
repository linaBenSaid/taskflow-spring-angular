package com.taskflow.controller;

import com.taskflow.dto.AddMemberRequest;
import com.taskflow.dto.ProjectMemberResponse;
import com.taskflow.dto.ProjectRequest;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.service.ProjectService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

// HTTP only: read the request, call ProjectService, pick the status code. All rules live in the service.
// principal.getUsername() is the logged-in user's email (set by JwtAuthenticationFilter).
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Projects and their members. Only members can see a project.")
public class ProjectController {

	private final ProjectService projectService;

	// GET /api/projects?page=0&size=20&sort=name,asc
	// PagedModel gives a stable JSON shape: { "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }
	@GetMapping
	@Operation(summary = "My projects", description = "Projects I am a member of, newest first. Page size is capped at 100.")
	public PagedModel<ProjectResponse> listMine(
			@AuthenticationPrincipal UserDetails principal,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return new PagedModel<>(projectService.listMine(principal.getUsername(), pageable));
	}

	// 201 Created, with a Location header pointing at the new project.
	@PostMapping
	@Operation(summary = "Create a project", description = "The caller becomes its owner and first member.")
	public ResponseEntity<ProjectResponse> create(
			@AuthenticationPrincipal UserDetails principal, @Valid @RequestBody ProjectRequest request) {
		ProjectResponse created = projectService.create(request, principal.getUsername());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.id())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a project", description = "404 if it does not exist or you are not a member.")
	public ProjectResponse get(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
		return projectService.get(id, principal.getUsername());
	}

	@PutMapping("/{id}")
	@Operation(summary = "Edit a project", description = "Owner only.")
	public ProjectResponse update(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
		return projectService.update(id, request, principal.getUsername());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a project", description = "Owner or ADMIN. Deletes its tasks too.")
	public void delete(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
		projectService.delete(id, principal.getUsername());
	}

	@GetMapping("/{id}/members")
	@Operation(summary = "List members")
	public List<ProjectMemberResponse> listMembers(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
		return projectService.listMembers(id, principal.getUsername());
	}

	@PostMapping("/{id}/members")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Add a member by email", description = "Owner only. The user must already have an account.")
	public ProjectMemberResponse addMember(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long id, @Valid @RequestBody AddMemberRequest request) {
		return projectService.addMember(id, request, principal.getUsername());
	}

	// Also used to leave a project: DELETE /api/projects/{id}/members/{myOwnUserId}
	@DeleteMapping("/{id}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Remove a member or leave", description = "Owner or ADMIN can remove anyone but the owner; any member can remove themselves. Their tasks become unassigned.")
	public void removeMember(
			@AuthenticationPrincipal UserDetails principal, @PathVariable Long id, @PathVariable Long userId) {
		projectService.removeMember(id, userId, principal.getUsername());
	}
}
