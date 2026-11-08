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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// End-to-end through the real stack: JWT filter -> CommentController -> CommentService -> Postgres.
// Everything is rolled back after each test.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
@AutoConfigureMockMvc
@Transactional
class CommentApiTest {

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
	private String comments;

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
		long taskId = idFromLocation(mockMvc.perform(as(alice, post("/api/projects/" + projectId + "/tasks")).content("""
						{"title": "Design the homepage"}
						"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION));
		comments = "/api/tasks/" + taskId + "/comments";
	}

	@Test
	void requiresLogin() throws Exception {
		mockMvc.perform(get(comments)).andExpect(status().isUnauthorized());
	}

	@Test
	void memberAddsComment() throws Exception {
		mockMvc.perform(as(bob, post(comments)).content("""
						{"body": "  Looks good to me  "}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.body").value("Looks good to me"))
				.andExpect(jsonPath("$.author.email").value("bob@example.com"))
				.andExpect(jsonPath("$.createdAt").exists());
	}

	@Test
	void rejectsBlankOrTooLongComment() throws Exception {
		mockMvc.perform(as(bob, post(comments)).content("""
						{"body": "   "}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.body").exists());

		mockMvc.perform(as(bob, post(comments)).content("{\"body\": \"%s\"}".formatted("x".repeat(2001))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.body").exists());
	}

	@Test
	void listsCommentsOldestFirstWithPaging() throws Exception {
		addComment(alice, "First");
		addComment(bob, "Second");
		addComment(alice, "Third");

		mockMvc.perform(as(bob, get(comments + "?size=2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].body", contains("First", "Second")))
				.andExpect(jsonPath("$.page.totalElements").value(3))
				.andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	void outsiderGetsNotFound() throws Exception {
		mockMvc.perform(as(carol, get(comments))).andExpect(status().isNotFound());
		mockMvc.perform(as(carol, post(comments)).content("""
						{"body": "Sneaky"}
						"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void unknownTaskIsNotFound() throws Exception {
		mockMvc.perform(as(alice, get("/api/tasks/999999/comments"))).andExpect(status().isNotFound());
	}

	@Test
	void commentsStayAfterTheAuthorLeavesButTheyLoseAccess() throws Exception {
		addComment(bob, "Written before leaving");

		mockMvc.perform(as(bob, delete("/api/projects/" + projectId + "/members/" + bob.getId())))
				.andExpect(status().isNoContent());

		mockMvc.perform(as(bob, get(comments))).andExpect(status().isNotFound());
		mockMvc.perform(as(alice, get(comments)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].author.email").value("bob@example.com"));
	}

	private void addComment(User author, String body) throws Exception {
		mockMvc.perform(as(author, post(comments)).content("{\"body\": \"%s\"}".formatted(body)))
				.andExpect(status().isCreated());
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
