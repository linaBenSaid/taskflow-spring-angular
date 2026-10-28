package com.taskflow.dto;

import java.time.Instant;

// Built by ProjectMapper from a ProjectMember and its user.
public record ProjectMemberResponse(Long userId, String email, String name, Instant joinedAt) {
}
