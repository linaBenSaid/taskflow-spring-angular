package com.taskflow.dto;

import com.taskflow.entity.TaskPriority;
import com.taskflow.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// Only the title is required. status defaults to TODO, priority to MEDIUM, assignee to nobody.
public record CreateTaskRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 5000) String description,
		TaskStatus status,
		TaskPriority priority,
		LocalDate dueDate,
		Long assigneeId
) {
}
