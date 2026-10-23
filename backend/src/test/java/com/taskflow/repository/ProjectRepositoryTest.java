package com.taskflow.repository;

import com.taskflow.TestcontainersConfiguration;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
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

// Runs the derived queries against a real Postgres (Testcontainers). Rolled back after each test.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
@Transactional
class ProjectRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private ProjectMemberRepository projectMemberRepository;

	@PersistenceContext
	private EntityManager entityManager;

	private User alice;
	private User bob;
	private User carol;
	private Project alpha;
	private Project beta;
	private Project gamma;

	// alice owns alpha and beta, bob owns gamma and is also a member of alpha, carol is in nothing.
	@BeforeEach
	void setUp() {
		alice = userRepository.save(user("alice@example.com"));
		bob = userRepository.save(user("bob@example.com"));
		carol = userRepository.save(user("carol@example.com"));

		alpha = projectRepository.save(project("Alpha", alice));
		beta = projectRepository.save(project("Beta", alice));
		gamma = projectRepository.save(project("Gamma", bob));

		alpha.addMember(bob);

		entityManager.flush();
		entityManager.clear(); // the queries below must read from the database, not from memory
	}

	@Test
	void findsOnlyProjectsTheUserIsAMemberOf() {
		Page<Project> alicesProjects = projectRepository.findByMembersUserId(alice.getId(), PageRequest.of(0, 10, Sort.by("name")));
		Page<Project> bobsProjects = projectRepository.findByMembersUserId(bob.getId(), PageRequest.of(0, 10, Sort.by("name")));
		Page<Project> carolsProjects = projectRepository.findByMembersUserId(carol.getId(), PageRequest.of(0, 10));

		assertThat(alicesProjects).extracting(Project::getName).containsExactly("Alpha", "Beta");
		assertThat(bobsProjects).extracting(Project::getName).containsExactly("Alpha", "Gamma");
		assertThat(carolsProjects).isEmpty();
	}

	@Test
	void pagesThroughProjects() {
		Page<Project> firstPage = projectRepository.findByMembersUserId(alice.getId(), PageRequest.of(0, 1, Sort.by("name")));

		assertThat(firstPage.getContent()).extracting(Project::getName).containsExactly("Alpha");
		assertThat(firstPage.getTotalElements()).isEqualTo(2);
		assertThat(firstPage.getTotalPages()).isEqualTo(2);
	}

	@Test
	void loadsOwnerInTheSameQuery() {
		Project project = projectRepository.findByMembersUserId(alice.getId(), PageRequest.of(0, 10)).getContent().get(0);

		// Without @EntityGraph the owner would be a lazy proxy here, loaded later by an extra query.
		assertThat(Hibernate.isInitialized(project.getOwner())).isTrue();
	}

	@Test
	void findsProjectByIdOnlyForMembers() {
		assertThat(projectRepository.findByIdAndMembersUserId(alpha.getId(), alice.getId())).isPresent();
		assertThat(projectRepository.findByIdAndMembersUserId(alpha.getId(), bob.getId())).isPresent();
		assertThat(projectRepository.findByIdAndMembersUserId(alpha.getId(), carol.getId())).isEmpty();
		assertThat(projectRepository.findByIdAndMembersUserId(gamma.getId(), alice.getId())).isEmpty();
	}

	@Test
	void checksMembership() {
		assertThat(projectMemberRepository.existsByProjectIdAndUserId(alpha.getId(), bob.getId())).isTrue();
		assertThat(projectMemberRepository.existsByProjectIdAndUserId(beta.getId(), bob.getId())).isFalse();
	}

	@Test
	void listsMembersWithTheirUser() {
		var members = projectMemberRepository.findByProjectIdOrderByJoinedAtAsc(alpha.getId());

		assertThat(members).extracting(member -> member.getUser().getEmail())
				.containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
		assertThat(members).allSatisfy(member -> assertThat(Hibernate.isInitialized(member.getUser())).isTrue());
		assertThat(members).extracting(ProjectMember::getJoinedAt).doesNotContainNull();
	}

	private static User user(String email) {
		User user = new User();
		user.setEmail(email);
		user.setName(email.substring(0, email.indexOf('@')));
		user.setPasswordHash("not-a-real-hash");
		return user;
	}

	private static Project project(String name, User owner) {
		Project project = new Project();
		project.setName(name);
		project.setOwner(owner);
		project.addMember(owner);
		return project;
	}
}
