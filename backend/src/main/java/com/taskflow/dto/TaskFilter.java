package com.taskflow.dto;

import com.taskflow.entity.TaskPriority;
import com.taskflow.entity.TaskStatus;

// Optional filters for listing a project's tasks. A null field means "don't filter on it".
// From GET /api/projects/{id}/tasks?status=TODO&priority=HIGH&assignee=3&q=login
public record TaskFilter(TaskStatus status, TaskPriority priority, Long assigneeId, String search) {
}
