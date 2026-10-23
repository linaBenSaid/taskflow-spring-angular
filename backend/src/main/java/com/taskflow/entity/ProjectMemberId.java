package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// Composite primary key of project_members: (project_id, user_id).
// JPA requires id classes to be Serializable and to implement equals/hashCode.
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProjectMemberId implements Serializable {

	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "user_id")
	private Long userId;
}
