package com.taskflow.mapper;

import com.taskflow.dto.ProjectMemberResponse;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.dto.TaskResponse;
import com.taskflow.dto.UserResponse;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.Task;
import com.taskflow.entity.TaskStatus;
import com.taskflow.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Plain unit tests for the MapStruct-generated classes: no Spring, no database.
class MapperTest {

	private final UserMapper userMapper = new UserMapperImpl();
	private final ProjectMapper projectMapper = new ProjectMapperImpl(userMapper);
	private final TaskMapper taskMapper = new TaskMapperImpl(userMapper);

	@Test
	void mapsUserWithoutPasswordHash() {
		UserResponse response = userMapper.toResponse(user());

		assertThat(response.id()).isEqualTo(1L);
		assertThat(response.email()).isEqualTo("alice@example.com");
		assertThat(response.role()).isEqualTo(Role.MEMBER);
		assertThat(response.toString()).doesNotContain("secret-hash"); // UserResponse has no such field
	}

	@Test
	void mapsProjectOwnerAsSummary() {
		Project project = project();

		ProjectResponse response = projectMapper.toResponse(project);

		assertThat(response.id()).isEqualTo(10L);
		assertThat(response.name()).isEqualTo("Website");
		assertThat(response.owner().email()).isEqualTo("alice@example.com");
	}

	@Test
	void mapsMemberFieldsFromItsUser() {
		Project project = project();
		ProjectMember member = project.addMember(project.getOwner());

		ProjectMemberResponse response = projectMapper.toResponse(member);

		assertThat(response.userId()).isEqualTo(1L);
		assertThat(response.email()).isEqualTo("alice@example.com");
		assertThat(response.name()).isEqualTo("Alice");
	}

	@Test
	void mapsTaskWithAndWithoutAssignee() {
		Task task = new Task();
		task.setId(100L);
		task.setProject(project());
		task.setTitle("Design");
		task.setStatus(TaskStatus.IN_PROGRESS);

		TaskResponse unassigned = taskMapper.toResponse(task);
		assertThat(unassigned.projectId()).isEqualTo(10L);
		assertThat(unassigned.status()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(unassigned.assignee()).isNull();

		task.setAssignee(user());
		assertThat(taskMapper.toResponse(task).assignee().name()).isEqualTo("Alice");
	}

	private static User user() {
		User user = new User();
		user.setId(1L);
		user.setEmail("alice@example.com");
		user.setName("Alice");
		user.setPasswordHash("secret-hash");
		user.setRole(Role.MEMBER);
		return user;
	}

	private static Project project() {
		Project project = new Project();
		project.setId(10L);
		project.setName("Website");
		project.setOwner(user());
		return project;
	}
}
