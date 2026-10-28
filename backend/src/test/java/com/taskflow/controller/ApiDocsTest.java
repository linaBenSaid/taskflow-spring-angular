package com.taskflow.controller;

import com.taskflow.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The API docs must be reachable without logging in and describe the API correctly.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
@AutoConfigureMockMvc
class ApiDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void openApiJsonIsPublicAndDescribesTheApi() throws Exception {
		mockMvc.perform(get("/v3/api-docs")) // no Authorization header
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("TaskFlow API"))
				.andExpect(jsonPath("$.paths['/api/projects']").exists())
				.andExpect(jsonPath("$.paths['/api/projects/{projectId}/tasks']").exists())
				.andExpect(jsonPath("$.paths['/api/tasks/{id}'].patch").exists())
				// The "Authorize" button: a JWT bearer scheme, required by default...
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.security[0].bearerAuth").exists())
				// ...except for login and register.
				.andExpect(jsonPath("$.paths['/api/auth/login'].post.security", empty()));
	}

	@Test
	void showsPagingAsQueryParametersAndHidesTheLoggedInUser() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/projects'].get.parameters[*].name",
						containsInAnyOrder("page", "size", "sort")))
				.andExpect(jsonPath("$.paths['/api/users/me'].get.parameters").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/projects/{projectId}/tasks'].get.parameters[*].name",
						not(hasItem("principal"))));
	}

	@Test
	void swaggerUiPageIsPublic() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
	}
}
