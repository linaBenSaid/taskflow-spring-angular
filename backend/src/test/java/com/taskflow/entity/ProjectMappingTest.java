package com.taskflow.entity;

import com.taskflow.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// Saves real rows in a Testcontainers Postgres to check the entity mappings and the V2 migration together.
// @Transactional rolls everything back after each test.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
@Transactional
class ProjectMappingTest {

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savesProjectWithOwnerAsMember() {
		User owner = user("owner@example.com");
		entityManager.persist(owner);

		Project project = new Project();
		project.setName("Website redesign");
		project.setOwner(owner);
		project.addMember(owner);
		entityManager.persist(project);

		entityManager.flush(); // send the INSERTs to the database
		entityManager.clear(); // forget the objects, so the next find really reads from the database

		Project loaded = entityManager.find(Project.class, project.getId());
		assertThat(loaded.getName()).isEqualTo("Website redesign");
		assertThat(loaded.getOwner().getEmail()).isEqualTo("owner@example.com");
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(loaded.getMembers()).singleElement().satisfies(member -> {
			assertThat(member.getUser().getId()).isEqualTo(owner.getId());
			assertThat(member.getId().getProjectId()).isEqualTo(project.getId());
			assertThat(member.getJoinedAt()).isNotNull();
		});
	}

	@Test
	void removingMemberDeletesItsRow() {
		User owner = user("owner@example.com");
		User member = user("member@example.com");
		entityManager.persist(owner);
		entityManager.persist(member);

		Project project = new Project();
		project.setName("Website redesign");
		project.setOwner(owner);
		project.addMember(owner);
		project.addMember(member);
		entityManager.persist(project);
		entityManager.flush();

		project.removeMember(member);
		entityManager.flush();
		entityManager.clear();

		Long rows = entityManager.createQuery(
						"select count(m) from ProjectMember m where m.project.id = :projectId", Long.class)
				.setParameter("projectId", project.getId())
				.getSingleResult();
		assertThat(rows).isEqualTo(1);
	}

	private static User user(String email) {
		User user = new User();
		user.setEmail(email);
		user.setName("Test User");
		user.setPasswordHash("not-a-real-hash");
		return user;
	}
}
