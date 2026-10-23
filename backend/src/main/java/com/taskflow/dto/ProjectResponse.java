package com.taskflow.dto;

import com.taskflow.entity.Project;

import java.time.Instant;

public record ProjectResponse(Long id, String name, String description, UserSummary owner, Instant createdAt) {

	public static ProjectResponse from(Project project) {
		return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
				UserSummary.from(project.getOwner()), project.getCreatedAt());
	}
}
