package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The limit matches the body column in V4__create_comments_table.sql.
public record CreateCommentRequest(@NotBlank @Size(max = 2000) String body) {
}
