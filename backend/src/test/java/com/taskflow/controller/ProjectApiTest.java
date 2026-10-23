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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// End-to-end through the real stack: JWT filter -> SecurityConfig -> ProjectController -> ProjectService -> Postgres.
// Checks the status codes and JSON the frontend will rely on. Everything is rolled back after each test.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
@AutoConfigureMockMvc
@Transactional
class ProjectApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	private User alice; // creates and owns the project
	private User bob;   // gets added as a member
	private User carol; // never a member
	private User admin;

	@BeforeEach
	void setUp() {
		alice = userRepository.save(user("alice@example.com", Role.MEMBER));
		bob = userRepository.save(user("bob@example.com", Role.MEMBER));
		carol = userRepository.save(user("carol@example.com", Role.MEMBER));
		admin = userRepository.save(user("admin@example.com", Role.ADMIN));
	}

	@Test
	void requiresLogin() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/projects"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createsProjectAndReturnsLocation() throws Exception {
		mockMvc.perform(as(alice, MockMvcRequestBuilders.post("/api/projects"))
						.content("""
								{"name": "Website", "description": "Redesign the marketing site"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*/api/projects/\\d+")))
				.andExpect(jsonPath("$.name").value("Website"))
				.andExpect(jsonPath("$.owner.email").value("alice@example.com"))
				.andExpect(jsonPath("$.createdAt").exists());
	}

	@Test
	void rejectsInvalidProject() throws Exception {
		mockMvc.perform(as(alice, MockMvcRequestBuilders.post("/api/projects")).content("""
						{"name": ""}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors.name").exists());
	}

	@Test
	void listsOnlyMyProjectsWithPaging() throws Exception {
		createProject(alice, "Alpha");
		createProject(alice, "Beta");
		createProject(carol, "Carol's project");

		mockMvc.perform(as(alice, MockMvcRequestBuilders.get("/api/projects?size=1&sort=name,asc")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].name").value("Alpha"))
				.andExpect(jsonPath("$.page.totalElements").value(2))
				.andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	void capsPageSizeAndRejectsUnknownSort() throws Exception {
		mockMvc.perform(as(alice, MockMvcRequestBuilders.get("/api/projects?size=5000")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page.size").value(100));

		mockMvc.perform(as(alice, MockMvcRequestBuilders.get("/api/projects?sort=doesNotExist")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid sort"));
	}

	@Test
	void nonMemberGetsNotFound() throws Exception {
		long projectId = createProject(alice, "Secret");

		mockMvc.perform(as(carol, MockMvcRequestBuilders.get("/api/projects/" + projectId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Not found"));
	}

	@Test
	void onlyOwnerCanEdit() throws Exception {
		long projectId = createProject(alice, "Website");
		addMember(alice, projectId, "bob@example.com");

		mockMvc.perform(as(bob, MockMvcRequestBuilders.put("/api/projects/" + projectId)).content("""
						{"name": "Renamed by Bob"}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.title").value("Forbidden"));

		mockMvc.perform(as(alice, MockMvcRequestBuilders.put("/api/projects/" + projectId)).content("""
						{"name": "Website v2", "description": "Second version"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Website v2"));
	}

	@Test
	void ownerManagesMembers() throws Exception {
		long projectId = createProject(alice, "Website");

		mockMvc.perform(as(alice, MockMvcRequestBuilders.post("/api/projects/" + projectId + "/members")).content("""
						{"email": "Bob@Example.com"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("bob@example.com"))
				.andExpect(jsonPath("$.joinedAt").exists());

		// Bob is now a member: he can see the project and its members.
		mockMvc.perform(as(bob, MockMvcRequestBuilders.get("/api/projects/" + projectId + "/members")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)));

		// Adding him again is a conflict, adding an unknown email is not found.
		mockMvc.perform(as(alice, MockMvcRequestBuilders.post("/api/projects/" + projectId + "/members")).content("""
						{"email": "bob@example.com"}
						"""))
				.andExpect(status().isConflict());
		mockMvc.perform(as(alice, MockMvcRequestBuilders.post("/api/projects/" + projectId + "/members")).content("""
						{"email": "nobody@example.com"}
						"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void memberCanLeaveButOwnerCannotBeRemoved() throws Exception {
		long projectId = createProject(alice, "Website");
		addMember(alice, projectId, "bob@example.com");

		mockMvc.perform(as(bob, MockMvcRequestBuilders.delete("/api/projects/" + projectId + "/members/" + bob.getId())))
				.andExpect(status().isNoContent());
		mockMvc.perform(as(bob, MockMvcRequestBuilders.get("/api/projects/" + projectId)))
				.andExpect(status().isNotFound());

		mockMvc.perform(as(alice, MockMvcRequestBuilders.delete("/api/projects/" + projectId + "/members/" + alice.getId())))
				.andExpect(status().isConflict());
	}

	@Test
	void memberCannotDeleteButAdminCan() throws Exception {
		long projectId = createProject(alice, "Website");
		addMember(alice, projectId, "bob@example.com");

		mockMvc.perform(as(bob, MockMvcRequestBuilders.delete("/api/projects/" + projectId)))
				.andExpect(status().isForbidden());

		mockMvc.perform(as(admin, MockMvcRequestBuilders.delete("/api/projects/" + projectId)))
				.andExpect(status().isNoContent());

		mockMvc.perform(as(alice, MockMvcRequestBuilders.get("/api/projects/" + projectId)))
				.andExpect(status().isNotFound());
	}

	// --- helpers ---

	private long createProject(User owner, String name) throws Exception {
		String location = mockMvc.perform(as(owner, MockMvcRequestBuilders.post("/api/projects"))
						.content("{\"name\": \"%s\"}".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	private void addMember(User owner, long projectId, String email) throws Exception {
		mockMvc.perform(as(owner, MockMvcRequestBuilders.post("/api/projects/" + projectId + "/members"))
						.content("{\"email\": \"%s\"}".formatted(email)))
				.andExpect(status().isCreated());
	}

	// Sends the request as this user, with a real JWT in the Authorization header.
	private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
		return request
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(user))
				.contentType(MediaType.APPLICATION_JSON);
	}

	private static User user(String email, Role role) {
		User user = new User();
		user.setEmail(email);
		user.setName(email.substring(0, email.indexOf('@')));
		user.setPasswordHash("not-a-real-hash");
		user.setRole(role);
		return user;
	}
}
