package com.taskflow.service;

import com.taskflow.dto.CommentResponse;
import com.taskflow.dto.CreateCommentRequest;
import com.taskflow.entity.Comment;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.mapper.CommentMapper;
import com.taskflow.mapper.CommentMapperImpl;
import com.taskflow.mapper.UserMapperImpl;
import com.taskflow.repository.CommentRepository;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for the comment rules. Repositories are mocks; entities are plain objects.
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

	private static final long PROJECT_ID = 10L;
	private static final long TASK_ID = 100L;
	private static final Pageable PAGE = PageRequest.of(0, 20);

	@Mock
	private CommentRepository commentRepository;

	@Mock
	private TaskRepository taskRepository;

	@Mock
	private ProjectMemberRepository projectMemberRepository;

	@Mock
	private UserRepository userRepository;

	// A real mapper (the classes MapStruct generated), handed to the service by @InjectMocks.
	@Spy
	private CommentMapper commentMapper = new CommentMapperImpl(new UserMapperImpl());

	@InjectMocks
	private CommentService commentService;

	private User member;
	private User outsider;
	private Task task;

	@BeforeEach
	void setUp() {
		member = user(2L, "member@example.com");
		outsider = user(3L, "outsider@example.com");

		Project project = new Project();
		project.setId(PROJECT_ID);

		task = new Task();
		task.setId(TASK_ID);
		task.setProject(project);
	}

	@Test
	void memberAddsCommentAsThemselves() {
		actingAs(member);
		taskExists();
		isMember(member, true);
		when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		CommentResponse response = commentService.add(TASK_ID, new CreateCommentRequest("  Looks good  "), member.getEmail());

		ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
		verify(commentRepository).save(saved.capture());
		assertThat(saved.getValue().getTask()).isSameAs(task);
		assertThat(saved.getValue().getAuthor()).isSameAs(member);
		assertThat(response.body()).isEqualTo("Looks good");
		assertThat(response.taskId()).isEqualTo(TASK_ID);
		assertThat(response.author().email()).isEqualTo("member@example.com");
	}

	@Test
	void outsiderCannotAddComment() {
		actingAs(outsider);
		taskExists();
		isMember(outsider, false);

		assertThatThrownBy(() -> commentService.add(TASK_ID, new CreateCommentRequest("Sneaky"), outsider.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(commentRepository, never()).save(any());
	}

	@Test
	void memberCanListComments() {
		Comment comment = new Comment();
		comment.setTask(task);
		comment.setAuthor(member);
		comment.setBody("Hello");
		Page<Comment> page = new PageImpl<>(List.of(comment), PAGE, 1);
		actingAs(member);
		taskExists();
		isMember(member, true);
		when(commentRepository.findByTaskId(TASK_ID, PAGE)).thenReturn(page);

		Page<CommentResponse> result = commentService.list(TASK_ID, PAGE, member.getEmail());

		assertThat(result.getContent()).extracting(CommentResponse::body).containsExactly("Hello");
	}

	@Test
	void outsiderCannotListComments() {
		actingAs(outsider);
		taskExists();
		isMember(outsider, false);

		assertThatThrownBy(() -> commentService.list(TASK_ID, PAGE, outsider.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(commentRepository, never()).findByTaskId(any(), any());
	}

	@Test
	void unknownTaskIsNotFound() {
		actingAs(member);
		when(taskRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> commentService.list(999L, PAGE, member.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	private void actingAs(User user) {
		when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
	}

	private void taskExists() {
		when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
	}

	private void isMember(User user, boolean isMember) {
		when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, user.getId())).thenReturn(isMember);
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
