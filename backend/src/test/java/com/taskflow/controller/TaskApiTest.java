package com.taskflow.controller;

import com.taskflow.TestcontainersConfiguration;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// End-to-end through the real stack: JWT filter -> TaskController -> TaskService -> Postgres.
// Everything is rolled back after each test.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
@AutoConfigureMockMvc
@Transactional
class TaskApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	private User alice; // owns the project
	private User bob;   // member
	private User carol; // not a member
	private long projectId;

	@BeforeEach
	void setUp() throws Exception {
		alice = userRepository.save(user("alice@example.com"));
		bob = userRepository.save(user("bob@example.com"));
		carol = userRepository.save(user("carol@example.com"));

		projectId = idFromLocation(mockMvc.perform(as(alice, post("/api/projects")).content("""
						{"name": "Website"}
						"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION));
		mockMvc.perform(as(alice, post("/api/projects/" + projectId + "/members")).content("""
						{"email": "bob@example.com"}
						"""))
				.andExpect(status().isCreated());
	}

	// --- create / read / delete ---

	@Test
	void createsTaskWithDefaults() throws Exception {
		mockMvc.perform(as(bob, post("/api/projects/" + projectId + "/tasks")).content("""
						{"title": "Design the homepage"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*/api/tasks/\\d+")))
				.andExpect(jsonPath("$.title").value("Design the homepage"))
				.andExpect(jsonPath("$.status").value("TODO"))
				.andExpect(jsonPath("$.priority").value("MEDIUM"))
				.andExpect(jsonPath("$.projectId").value(projectId))
				.andExpect(jsonPath("$.assignee").value(nullValue()));
	}

	@Test
	void rejectsInvalidTask() throws Exception {
		mockMvc.perform(as(bob, post("/api/projects/" + projectId + "/tasks")).content("""
						{"title": "", "status": "NOT_A_STATUS"}
						"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(as(bob, post("/api/projects/" + projectId + "/tasks")).content("""
						{"title": "Review", "assigneeId": %d}
						""".formatted(carol.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"));
	}

	@Test
	void outsiderSeesNothing() throws Exception {
		long taskId = createTask(bob, """
				{"title": "Private"}
				""");

		mockMvc.perform(as(carol, get("/api/projects/" + projectId + "/tasks"))).andExpect(status().isNotFound());
		mockMvc.perform(as(carol, post("/api/projects/" + projectId + "/tasks")).content("""
						{"title": "Sneaky"}
						"""))
				.andExpect(status().isNotFound());
		mockMvc.perform(as(carol, get("/api/tasks/" + taskId))).andExpect(status().isNotFound());
		mockMvc.perform(as(carol, patch("/api/tasks/" + taskId)).content("""
						{"status": "DONE"}
						"""))
				.andExpect(status().isNotFound());
		mockMvc.perform(as(carol, delete("/api/tasks/" + taskId))).andExpect(status().isNotFound());
	}

	@Test
	void deletesTask() throws Exception {
		long taskId = createTask(bob, """
				{"title": "Temporary"}
				""");

		mockMvc.perform(as(alice, delete("/api/tasks/" + taskId))).andExpect(status().isNoContent());
		mockMvc.perform(as(alice, get("/api/tasks/" + taskId))).andExpect(status().isNotFound());
	}

	// --- filters and paging ---

	@Test
	void filtersAndSearchesTasks() throws Exception {
		createFilterFixtures();
		String tasks = "/api/projects/" + projectId + "/tasks";

		expectTitles(get(tasks + "?status=TODO"), "Fix login bug", "Reach 100% coverage");
		expectTitles(get(tasks + "?priority=HIGH"), "Fix login bug", "Login page copy");
		expectTitles(get(tasks + "?assignee=" + bob.getId()), "Fix login bug", "Reach 100% coverage");
		expectTitles(get(tasks + "?status=TODO&priority=HIGH&assignee=" + bob.getId()), "Fix login bug");
		expectTitles(get(tasks + "?q=LOGIN"), "Fix login bug", "Login page copy"); // case-insensitive
		expectTitles(get(tasks).param("q", "100%"), "Reach 100% coverage");      // % is matched literally
		expectTitles(get(tasks));                                                // no filter: all 4
	}

	@Test
	void pagesAndRejectsUnknownFilterValues() throws Exception {
		createFilterFixtures();
		String tasks = "/api/projects/" + projectId + "/tasks";

		mockMvc.perform(as(alice, get(tasks + "?size=3&sort=title,asc")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(3)))
				.andExpect(jsonPath("$.content[0].title").value("Fix login bug"))
				.andExpect(jsonPath("$.page.totalElements").value(4))
				.andExpect(jsonPath("$.page.totalPages").value(2));

		mockMvc.perform(as(alice, get(tasks + "?status=FINISHED"))).andExpect(status().isBadRequest());
	}

	// --- PATCH over real HTTP: missing field = keep, null = clear ---

	@Test
	void patchChangesOnlyTheFieldsSent() throws Exception {
		long taskId = createTask(alice, """
				{"title": "Fix login bug", "dueDate": "2026-11-30", "assigneeId": %d}
				""".formatted(bob.getId()));

		// Dragging the card to "Done": nothing else may change.
		mockMvc.perform(as(bob, patch("/api/tasks/" + taskId)).content("""
						{"status": "DONE"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"))
				.andExpect(jsonPath("$.title").value("Fix login bug"))
				.andExpect(jsonPath("$.dueDate").value("2026-11-30"))
				.andExpect(jsonPath("$.assignee.email").value("bob@example.com"));

		// Explicit null: unassign and remove the due date.
		mockMvc.perform(as(bob, patch("/api/tasks/" + taskId)).content("""
						{"assigneeId": null, "dueDate": null}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assignee").value(nullValue()))
				.andExpect(jsonPath("$.dueDate").value(nullValue()))
				.andExpect(jsonPath("$.status").value("DONE"));
	}

	@Test
	void patchRejectsBlankTitleAndOutsideAssignee() throws Exception {
		long taskId = createTask(alice, """
				{"title": "Fix login bug"}
				""");

		mockMvc.perform(as(bob, patch("/api/tasks/" + taskId)).content("""
						{"title": "   "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.title").exists());

		mockMvc.perform(as(bob, patch("/api/tasks/" + taskId)).content("""
						{"assigneeId": %d}
						""".formatted(carol.getId())))
				.andExpect(status().isBadRequest());
	}

	@Test
	void leavingTheProjectUnassignsYourTasks() throws Exception {
		long taskId = createTask(alice, """
				{"title": "Fix login bug", "assigneeId": %d}
				""".formatted(bob.getId()));

		mockMvc.perform(as(bob, delete("/api/projects/" + projectId + "/members/" + bob.getId())))
				.andExpect(status().isNoContent());

		mockMvc.perform(as(alice, get("/api/tasks/" + taskId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assignee").value(nullValue()));
	}

	// --- helpers ---

	// Four tasks covering different statuses, priorities and assignees.
	private void createFilterFixtures() throws Exception {
		createTask(alice, """
				{"title": "Fix login bug", "status": "TODO", "priority": "HIGH", "assigneeId": %d}
				""".formatted(bob.getId()));
		createTask(alice, """
				{"title": "Write docs", "status": "IN_PROGRESS", "priority": "LOW", "assigneeId": %d}
				""".formatted(alice.getId()));
		createTask(alice, """
				{"title": "Login page copy", "status": "DONE", "priority": "HIGH"}
				""");
		createTask(alice, """
				{"title": "Reach 100%% coverage", "status": "TODO", "priority": "MEDIUM", "assigneeId": %d}
				""".formatted(bob.getId()));
	}

	// Expects exactly these titles (any order). No titles given = all four fixtures.
	private void expectTitles(MockHttpServletRequestBuilder request, String... titles) throws Exception {
		if (titles.length == 0) {
			mockMvc.perform(as(alice, request)).andExpect(jsonPath("$.page.totalElements").value(4));
			return;
		}
		mockMvc.perform(as(alice, request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].title", containsInAnyOrder(titles)));
	}

	private long createTask(User author, String json) throws Exception {
		return idFromLocation(mockMvc.perform(as(author, post("/api/projects/" + projectId + "/tasks")).content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION));
	}

	private static long idFromLocation(String location) {
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	// Sends the request as this user, with a real JWT in the Authorization header.
	private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
		return request
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(user))
				.contentType(MediaType.APPLICATION_JSON);
	}

	private static User user(String email) {
		User user = new User();
		user.setEmail(email);
		user.setName(email.substring(0, email.indexOf('@')));
		user.setPasswordHash("not-a-real-hash");
		user.setRole(Role.MEMBER);
		return user;
	}
}
