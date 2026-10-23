package com.taskflow.dto;

import com.taskflow.entity.User;

// The few user fields other resources show (a project's owner, a task's assignee).
public record UserSummary(Long id, String email, String name) {

	public static UserSummary from(User user) {
		return new UserSummary(user.getId(), user.getEmail(), user.getName());
	}
}
