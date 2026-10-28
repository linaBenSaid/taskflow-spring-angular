package com.taskflow.dto;

import java.time.Instant;

// Built by ProjectMapper.
public record ProjectResponse(Long id, String name, String description, UserSummary owner, Instant createdAt) {
}
