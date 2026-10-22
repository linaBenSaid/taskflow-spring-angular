package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

// One row of project_members: "this user is a member of this project since joinedAt".
@Entity
@Table(name = "project_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only; use the constructor below
public class ProjectMember {

	@EmbeddedId
	private ProjectMemberId id = new ProjectMemberId();

	// @MapsId: the id's projectId/userId are filled from these relations when the row is saved.
	@MapsId("projectId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id")
	private Project project;

	@MapsId("userId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@CreationTimestamp
	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	public ProjectMember(Project project, User user) {
		this.project = project;
		this.user = user;
	}
}
