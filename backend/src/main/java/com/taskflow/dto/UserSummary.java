package com.taskflow.dto;

// The few user fields other resources show (a project's owner, a task's assignee). Built by UserMapper.
public record UserSummary(Long id, String email, String name) {
}
