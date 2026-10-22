package com.taskflow.dto;

import com.taskflow.entity.Role;
import com.taskflow.entity.User;

import java.time.Instant;

// What the API returns for a user. Never includes the password hash.
public record UserResponse(Long id, String email, String name, Role role, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getCreatedAt());
	}
}
