package com.taskflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Fixed JWT secret so this test does not depend on a local .env file (for example in CI).
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=test-secret-that-is-at-least-32-characters-long")
class TaskflowApplicationTests {

	@Test
	void contextLoads() {
	}

}
