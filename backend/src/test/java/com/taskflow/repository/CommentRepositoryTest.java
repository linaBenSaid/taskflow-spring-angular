package com.taskflow.repository;

import com.taskflow.TestcontainersConfiguration;
import com.taskflow.entity.Comment;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// Checks the Comment mapping, the V4 migration and the query against a real Postgres. Rolled back after each test.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
@Transactional
class CommentRepositoryTest {

	private static final Sort OLDEST_FIRST = Sort.by("createdAt", "id");

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private TaskRepository taskRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UserRepository userRepository;

	@PersistenceContext
	private EntityManager entityManager;

	private User alice;
	private Task design;
	private Task docs;

	@BeforeEach
	void setUp() {
		alice = new User();
		alice.setEmail("alice@example.com");
		alice.setName("Alice");
		alice.setPasswordHash("not-a-real-hash");
		userRepository.save(alice);

		Project project = new Project();
		project.setName("Website");
		project.setOwner(alice);
		project.addMember(alice);
		projectRepository.save(project);

		design = taskRepository.save(task(project, "Design"));
		docs = taskRepository.save(task(project, "Docs"));
	}

	@Test
	void findsOnlyThatTasksCommentsOldestFirst() {
		commentRepository.save(comment(design, "First"));
		commentRepository.save(comment(docs, "On another task"));
		commentRepository.save(comment(design, "Second"));
		entityManager.flush();
		entityManager.clear();

		Page<Comment> comments = commentRepository.findByTaskId(design.getId(), PageRequest.of(0, 10, OLDEST_FIRST));

		assertThat(comments).extracting(Comment::getBody).containsExactly("First", "Second");
		assertThat(comments.getContent().get(0).getCreatedAt()).isNotNull();
	}

	@Test
	void pagesThroughComments() {
		commentRepository.save(comment(design, "First"));
		commentRepository.save(comment(design, "Second"));
		commentRepository.save(comment(design, "Third"));
		entityManager.flush();
		entityManager.clear();

		Page<Comment> firstPage = commentRepository.findByTaskId(design.getId(), PageRequest.of(0, 2, OLDEST_FIRST));

		assertThat(firstPage.getContent()).hasSize(2);
		assertThat(firstPage.getTotalElements()).isEqualTo(3);
	}

	@Test
	void loadsAuthorInTheSameQuery() {
		commentRepository.save(comment(design, "First"));
		entityManager.flush();
		entityManager.clear();

		Comment loaded = commentRepository.findByTaskId(design.getId(), PageRequest.of(0, 10)).getContent().get(0);

		assertThat(Hibernate.isInitialized(loaded.getAuthor())).isTrue();
		assertThat(loaded.getAuthor().getEmail()).isEqualTo("alice@example.com");
	}

	@Test
	void deletingTaskDeletesItsComments() {
		commentRepository.save(comment(design, "Goes away with the task"));
		commentRepository.save(comment(docs, "Stays"));
		entityManager.flush();
		entityManager.clear();

		entityManager.remove(entityManager.find(Task.class, design.getId()));
		entityManager.flush(); // the database's ON DELETE CASCADE removes the comment row

		assertThat(commentRepository.findAll()).extracting(Comment::getBody).containsExactly("Stays");
	}

	private static Task task(Project project, String title) {
		Task task = new Task();
		task.setProject(project);
		task.setTitle(title);
		return task;
	}

	private Comment comment(Task task, String body) {
		Comment comment = new Comment();
		comment.setTask(task);
		comment.setAuthor(alice);
		comment.setBody(body);
		return comment;
	}
}
