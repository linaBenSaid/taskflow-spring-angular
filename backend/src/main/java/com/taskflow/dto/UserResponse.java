package com.taskflow.dto;

import com.taskflow.entity.Role;

import java.time.Instant;

// What the API returns for a user. Never includes the password hash. Built by UserMapper.
public record UserResponse(Long id, String email, String name, Role role, Instant createdAt) {
}
