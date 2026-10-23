package com.taskflow.repository;

import com.taskflow.TestcontainersConfiguration;
import com.taskflow.entity.Project;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// Runs TaskRepository's queries against a real Postgres (Testcontainers). Rolled back after each test.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
@Transactional
class TaskRepositoryTest {

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@PersistenceContext
	private EntityManager entityManager;

	private User alice;
	private User bob;
	private Project website;
	private Project mobile;

	// alice and bob are in both projects.
	@BeforeEach
	void setUp() {
		alice = userRepository.save(user("alice@example.com"));
		bob = userRepository.save(user("bob@example.com"));
		website = projectRepository.save(project("Website", alice, bob));
		mobile = projectRepository.save(project("Mobile", alice, bob));
	}

	@Test
	void unassignsOnlyThatUsersTasksInThatProject() {
		Task bobsWebsiteTask = taskRepository.save(task(website, "Bob, website", bob));
		Task bobsMobileTask = taskRepository.save(task(mobile, "Bob, mobile", bob));
		Task alicesWebsiteTask = taskRepository.save(task(website, "Alice, website", alice));
		entityManager.flush();

		int updated = taskRepository.unassignAllInProject(website.getId(), bob.getId());
		entityManager.clear(); // the bulk UPDATE bypassed the loaded objects, so reload from the database

		assertThat(updated).isEqualTo(1);
		assertThat(reload(bobsWebsiteTask).getAssignee()).isNull();
		assertThat(reload(bobsMobileTask).getAssignee().getId()).isEqualTo(bob.getId());
		assertThat(reload(alicesWebsiteTask).getAssignee().getId()).isEqualTo(alice.getId());
	}

	@Test
	void loadsAssigneeWithTheTask() {
		Task saved = taskRepository.save(task(website, "Design", bob));
		entityManager.flush();
		entityManager.clear();

		Task loaded = taskRepository.findWithAssigneeById(saved.getId()).orElseThrow();

		assertThat(Hibernate.isInitialized(loaded.getAssignee())).isTrue();
	}

	private Task reload(Task task) {
		return taskRepository.findById(task.getId()).orElseThrow();
	}

	private static User user(String email) {
		User user = new User();
		user.setEmail(email);
		user.setName(email.substring(0, email.indexOf('@')));
		user.setPasswordHash("not-a-real-hash");
		return user;
	}

	private static Project project(String name, User owner, User otherMember) {
		Project project = new Project();
		project.setName(name);
		project.setOwner(owner);
		project.addMember(owner);
		project.addMember(otherMember);
		return project;
	}

	private static Task task(Project project, String title, User assignee) {
		Task task = new Task();
		task.setProject(project);
		task.setTitle(title);
		task.setAssignee(assignee);
		return task;
	}
}
