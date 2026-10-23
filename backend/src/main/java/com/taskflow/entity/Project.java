package com.taskflow.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

// Maps to the "projects" table created by V2__create_projects_tables.sql.
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 2000)
	private String description;

	// LAZY: the owner is only loaded from the database when the code actually uses it.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	// Saving or deleting a project also saves or deletes its member rows.
	// orphanRemoval: removing a member from this set deletes its row.
	@OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
	@Setter(lombok.AccessLevel.NONE) // change members only through addMember/removeMember
	private Set<ProjectMember> members = new HashSet<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public ProjectMember addMember(User user) {
		ProjectMember member = new ProjectMember(this, user);
		members.add(member);
		return member;
	}

	public void removeMember(User user) {
		removeMember(user.getId());
	}

	public void removeMember(Long userId) {
		members.removeIf(member -> member.getUser().getId().equals(userId));
	}
}
