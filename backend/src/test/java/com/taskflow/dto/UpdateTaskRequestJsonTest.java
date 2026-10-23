package com.taskflow.dto;

import com.taskflow.entity.TaskStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Proves the three states PATCH relies on: missing field -> null, explicit null -> Optional.empty(), value -> Optional.of(value).
class UpdateTaskRequestJsonTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void missingFieldsAreNull() {
		UpdateTaskRequest request = jsonMapper.readValue("""
				{"status": "DONE"}
				""", UpdateTaskRequest.class);

		assertThat(request.getStatus()).isEqualTo(TaskStatus.DONE);
		assertThat(request.getTitle()).isNull();
		assertThat(request.getAssigneeId()).isNull();
		assertThat(request.getDueDate()).isNull();
		assertThat(request.getDescription()).isNull();
	}

	@Test
	void explicitNullIsEmptyOptional() {
		UpdateTaskRequest request = jsonMapper.readValue("""
				{"assigneeId": null, "dueDate": null, "description": null}
				""", UpdateTaskRequest.class);

		assertThat(request.getAssigneeId()).isEmpty();
		assertThat(request.getDueDate()).isEmpty();
		assertThat(request.getDescription()).isEmpty();
	}

	@Test
	void valuesAreWrapped() {
		UpdateTaskRequest request = jsonMapper.readValue("""
				{"assigneeId": 7, "dueDate": "2026-11-30", "description": "Details"}
				""", UpdateTaskRequest.class);

		assertThat(request.getAssigneeId()).contains(7L);
		assertThat(request.getDueDate()).contains(LocalDate.of(2026, 11, 30));
		assertThat(request.getDescription()).isEqualTo(Optional.of("Details"));
	}
}
