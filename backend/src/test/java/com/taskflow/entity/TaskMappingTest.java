package com.taskflow.entity;

import com.taskflow.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Saves real rows in a Testcontainers Postgres to check the Task mapping and the V3 migration together.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
@Transactional
class TaskMappingTest {

	@PersistenceContext
	private EntityManager entityManager;

	private User owner;
	private Project project;

	@BeforeEach
	void setUp() {
		owner = new User();
		owner.setEmail("owner@example.com");
		owner.setName("Owner");
		owner.setPasswordHash("not-a-real-hash");
		entityManager.persist(owner);

		project = new Project();
		project.setName("Website");
		project.setOwner(owner);
		project.addMember(owner);
		entityManager.persist(project);
	}

	@Test
	void savesTaskWithDefaultsAndAssignee() {
		Task task = new Task();
		task.setProject(project);
		task.setTitle("Design the homepage");
		task.setDueDate(LocalDate.of(2026, 11, 30));
		task.setAssignee(owner);
		entityManager.persist(task);
		entityManager.flush();
		entityManager.clear();

		Task loaded = entityManager.find(Task.class, task.getId());
		assertThat(loaded.getTitle()).isEqualTo("Design the homepage");
		assertThat(loaded.getStatus()).isEqualTo(TaskStatus.TODO);
		assertThat(loaded.getPriority()).isEqualTo(TaskPriority.MEDIUM);
		assertThat(loaded.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 30));
		assertThat(loaded.getAssignee().getEmail()).isEqualTo("owner@example.com");
		assertThat(loaded.getProject().getId()).isEqualTo(project.getId());
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(loaded.getUpdatedAt()).isNotNull();
	}

	@Test
	void savesStatusChange() {
		Task task = new Task();
		task.setProject(project);
		task.setTitle("Write copy");
		entityManager.persist(task);
		entityManager.flush();

		task.setStatus(TaskStatus.IN_PROGRESS); // no save() call: Hibernate notices the change (dirty checking)
		entityManager.flush();
		entityManager.clear();

		assertThat(entityManager.find(Task.class, task.getId()).getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
	}

	@Test
	void deletingProjectDeletesItsTasks() {
		Task task = new Task();
		task.setProject(project);
		task.setTitle("Will be deleted with the project");
		entityManager.persist(task);
		entityManager.flush();
		entityManager.clear();

		entityManager.remove(entityManager.find(Project.class, project.getId()));
		entityManager.flush(); // the database's ON DELETE CASCADE removes the task row

		Long remaining = entityManager.createQuery("select count(t) from Task t", Long.class).getSingleResult();
		assertThat(remaining).isZero();
	}
}
