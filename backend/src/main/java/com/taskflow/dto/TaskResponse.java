package com.taskflow.dto;

import com.taskflow.entity.TaskPriority;
import com.taskflow.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

// assignee is null when the task is unassigned. Built by TaskMapper.
public record TaskResponse(
		Long id,
		Long projectId,
		String title,
		String description,
		TaskStatus status,
		TaskPriority priority,
		LocalDate dueDate,
		UserSummary assignee,
		Instant createdAt,
		Instant updatedAt
) {
}
