package com.taskflow.dto;

import java.time.Instant;

// author is null if the author's account was deleted. Built by CommentMapper.
public record CommentResponse(Long id, Long taskId, UserSummary author, String body, Instant createdAt) {
}
