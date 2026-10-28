package com.taskflow.service;

import com.taskflow.dto.CreateTaskRequest;
import com.taskflow.dto.TaskResponse;
import com.taskflow.dto.UpdateTaskRequest;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.Task;
import com.taskflow.entity.TaskPriority;
import com.taskflow.entity.TaskStatus;
import com.taskflow.entity.User;
import com.taskflow.exception.InvalidRequestException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.mapper.TaskMapper;
import com.taskflow.mapper.TaskMapperImpl;
import com.taskflow.mapper.UserMapperImpl;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for the task rules. Repositories are mocks; entities are plain objects.
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

	private static final long PROJECT_ID = 10L;
	private static final long TASK_ID = 100L;
	private static final LocalDate DUE = LocalDate.of(2026, 11, 30);

	@Mock
	private TaskRepository taskRepository;

	@Mock
	private ProjectRepository projectRepository;

	@Mock
	private ProjectMemberRepository projectMemberRepository;

	@Mock
	private UserRepository userRepository;

	// A real mapper (the classes MapStruct generated), handed to the service by @InjectMocks.
	@Spy
	private TaskMapper taskMapper = new TaskMapperImpl(new UserMapperImpl());

	@InjectMocks
	private TaskService taskService;

	// owner and member are in the project, outsider isn't. The task is assigned to owner and due on DUE.
	private User owner;
	private User member;
	private User outsider;
	private Project project;
	private Task task;

	@BeforeEach
	void setUp() {
		owner = user(1L, "owner@example.com");
		member = user(2L, "member@example.com");
		outsider = user(3L, "outsider@example.com");

		project = new Project();
		project.setId(PROJECT_ID);
		project.setName("Website");
		project.setOwner(owner);

		task = new Task();
		task.setId(TASK_ID);
		task.setProject(project);
		task.setTitle("Design the homepage");
		task.setDescription("Hero section first");
		task.setAssignee(owner);
		task.setDueDate(DUE);
	}

	// --- create ---

	@Test
	void memberCreatesTaskWithDefaults() {
		actingAs(member);
		when(projectRepository.findByIdAndMembersUserId(PROJECT_ID, member.getId())).thenReturn(Optional.of(project));
		when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TaskResponse response = taskService.create(PROJECT_ID,
				new CreateTaskRequest("  Write tests ", " ", null, null, null, null), member.getEmail());

		assertThat(response.title()).isEqualTo("Write tests");
		assertThat(response.description()).isNull();
		assertThat(response.status()).isEqualTo(TaskStatus.TODO);
		assertThat(response.priority()).isEqualTo(TaskPriority.MEDIUM);
		assertThat(response.assignee()).isNull();
		assertThat(response.projectId()).isEqualTo(PROJECT_ID);
	}

	@Test
	void createsTaskAssignedToAMember() {
		actingAs(member);
		when(projectRepository.findByIdAndMembersUserId(PROJECT_ID, member.getId())).thenReturn(Optional.of(project));
		isMember(owner, true);
		when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
		when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TaskResponse response = taskService.create(PROJECT_ID,
				new CreateTaskRequest("Review", null, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, DUE, owner.getId()),
				member.getEmail());

		assertThat(response.assignee().email()).isEqualTo("owner@example.com");
		assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
		assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
		assertThat(response.dueDate()).isEqualTo(DUE);
	}

	@Test
	void cannotAssignSomeoneOutsideTheProject() {
		actingAs(member);
		when(projectRepository.findByIdAndMembersUserId(PROJECT_ID, member.getId())).thenReturn(Optional.of(project));
		isMember(outsider, false);

		assertThatThrownBy(() -> taskService.create(PROJECT_ID,
				new CreateTaskRequest("Review", null, null, null, null, outsider.getId()), member.getEmail()))
				.isInstanceOf(InvalidRequestException.class);
		verify(taskRepository, never()).save(any());
	}

	@Test
	void outsiderCannotCreateTasks() {
		actingAs(outsider);
		when(projectRepository.findByIdAndMembersUserId(PROJECT_ID, outsider.getId())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> taskService.create(PROJECT_ID,
				new CreateTaskRequest("Sneaky", null, null, null, null, null), outsider.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	// --- get ---

	@Test
	void memberCanReadTask() {
		actingAs(member);
		taskExists();
		isMember(member, true);

		assertThat(taskService.get(TASK_ID, member.getEmail()).title()).isEqualTo("Design the homepage");
	}

	@Test
	void outsiderGetsNotFound() {
		actingAs(outsider);
		taskExists();
		isMember(outsider, false);

		assertThatThrownBy(() -> taskService.get(TASK_ID, outsider.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void unknownTaskIsNotFound() {
		actingAs(member);
		when(taskRepository.findWithAssigneeById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> taskService.get(999L, member.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	// --- update (PATCH) ---

	@Test
	void patchWithOnlyStatusLeavesEverythingElseAlone() {
		actingAs(member);
		taskExists();
		isMember(member, true);
		UpdateTaskRequest request = new UpdateTaskRequest();
		request.setStatus(TaskStatus.DONE); // what dragging a card to "Done" sends

		TaskResponse response = taskService.update(TASK_ID, request, member.getEmail());

		assertThat(response.status()).isEqualTo(TaskStatus.DONE);
		assertThat(task.getTitle()).isEqualTo("Design the homepage");
		assertThat(task.getDescription()).isEqualTo("Hero section first");
		assertThat(task.getAssignee()).isSameAs(owner);
		assertThat(task.getDueDate()).isEqualTo(DUE);
		verify(taskRepository).flush();
	}

	@Test
	void patchWithExplicitNullsClearsFields() {
		actingAs(member);
		taskExists();
		isMember(member, true);
		UpdateTaskRequest request = new UpdateTaskRequest();
		request.setAssigneeId(Optional.empty()); // "assigneeId": null
		request.setDueDate(Optional.empty());    // "dueDate": null
		request.setDescription(Optional.empty()); // "description": null

		taskService.update(TASK_ID, request, member.getEmail());

		assertThat(task.getAssignee()).isNull();
		assertThat(task.getDueDate()).isNull();
		assertThat(task.getDescription()).isNull();
		assertThat(task.getTitle()).isEqualTo("Design the homepage");
	}

	@Test
	void patchChangesTitleAndReassigns() {
		actingAs(member);
		taskExists();
		isMember(member, true);
		when(userRepository.findById(member.getId())).thenReturn(Optional.of(member));
		UpdateTaskRequest request = new UpdateTaskRequest();
		request.setTitle("  Design the landing page ");
		request.setAssigneeId(Optional.of(member.getId()));

		taskService.update(TASK_ID, request, member.getEmail());

		assertThat(task.getTitle()).isEqualTo("Design the landing page");
		assertThat(task.getAssignee()).isSameAs(member);
	}

	@Test
	void patchCannotAssignSomeoneOutsideTheProject() {
		actingAs(member);
		taskExists();
		isMember(member, true);
		isMember(outsider, false);
		UpdateTaskRequest request = new UpdateTaskRequest();
		request.setAssigneeId(Optional.of(outsider.getId()));

		assertThatThrownBy(() -> taskService.update(TASK_ID, request, member.getEmail()))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(task.getAssignee()).isSameAs(owner);
	}

	@Test
	void outsiderCannotUpdate() {
		actingAs(outsider);
		taskExists();
		isMember(outsider, false);
		UpdateTaskRequest request = new UpdateTaskRequest();
		request.setStatus(TaskStatus.DONE);

		assertThatThrownBy(() -> taskService.update(TASK_ID, request, outsider.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
		assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
	}

	// --- delete ---

	@Test
	void memberCanDeleteTask() {
		actingAs(member);
		taskExists();
		isMember(member, true);

		taskService.delete(TASK_ID, member.getEmail());

		verify(taskRepository).delete(task);
	}

	@Test
	void outsiderCannotDeleteTask() {
		actingAs(outsider);
		taskExists();
		isMember(outsider, false);

		assertThatThrownBy(() -> taskService.delete(TASK_ID, outsider.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(taskRepository, never()).delete(any(Task.class));
	}

	// --- helpers ---

	private void actingAs(User user) {
		when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
	}

	private void taskExists() {
		when(taskRepository.findWithAssigneeById(TASK_ID)).thenReturn(Optional.of(task));
	}

	private void isMember(User user, boolean member) {
		when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, user.getId())).thenReturn(member);
	}

	private static User user(Long id, String email) {
		User user = new User();
		user.setId(id);
		user.setEmail(email);
		user.setName(email.substring(0, email.indexOf('@')));
		user.setPasswordHash("hash");
		user.setRole(Role.MEMBER);
		return user;
	}
}
