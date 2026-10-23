package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of "create project" and "edit project". Limits match the columns in V2__create_projects_tables.sql.
public record ProjectRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 2000) String description
) {
}
