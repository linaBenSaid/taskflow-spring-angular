package com.taskflow.entity;

// The three Kanban columns. Must match tasks_status_check in V3__create_tasks_table.sql.
public enum TaskStatus {
	TODO,
	IN_PROGRESS,
	DONE
}
