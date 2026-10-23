package com.taskflow.dto;

import com.taskflow.entity.Task;
import com.taskflow.entity.TaskPriority;
import com.taskflow.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

// assignee is null when the task is unassigned.
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

	public static TaskResponse from(Task task) {
		return new TaskResponse(
				task.getId(),
				task.getProject().getId(),
				task.getTitle(),
				task.getDescription(),
				task.getStatus(),
				task.getPriority(),
				task.getDueDate(),
				task.getAssignee() == null ? null : UserSummary.from(task.getAssignee()),
				task.getCreatedAt(),
				task.getUpdatedAt());
	}
}
