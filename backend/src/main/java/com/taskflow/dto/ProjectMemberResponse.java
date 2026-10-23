package com.taskflow.dto;

import com.taskflow.entity.ProjectMember;

import java.time.Instant;

public record ProjectMemberResponse(Long userId, String email, String name, Instant joinedAt) {

	public static ProjectMemberResponse from(ProjectMember member) {
		return new ProjectMemberResponse(member.getUser().getId(), member.getUser().getEmail(),
				member.getUser().getName(), member.getJoinedAt());
	}
}
