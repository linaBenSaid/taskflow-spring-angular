package com.taskflow.service;

import com.taskflow.dto.AddMemberRequest;
import com.taskflow.dto.ProjectMemberResponse;
import com.taskflow.dto.ProjectRequest;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.exception.ConflictException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.mapper.ProjectMapper;
import com.taskflow.mapper.ProjectMapperImpl;
import com.taskflow.mapper.UserMapperImpl;
import com.taskflow.repository.ProjectMemberRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for the access rules. Repositories are mocks; entities are plain objects.
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

	private static final long PROJECT_ID = 10L;

	@Mock
	private ProjectRepository projectRepository;

	@Mock
	private ProjectMemberRepository projectMemberRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private TaskRepository taskRepository;

	// A real mapper (the classes MapStruct generated), handed to the service by @InjectMocks.
	@Spy
	private ProjectMapper projectMapper = new ProjectMapperImpl(new UserMapperImpl());

	@InjectMocks
	private ProjectService projectService;

	// owner owns the project, member is in it, outsider isn't, admin isn't either but is an ADMIN.
	private User owner;
	private User member;
	private User outsider;
	private User admin;
	private Project project;

	@BeforeEach
	void setUp() {
		owner = user(1L, "owner@example.com", Role.MEMBER);
		member = user(2L, "member@example.com", Role.MEMBER);
		outsider = user(3L, "outsider@example.com", Role.MEMBER);
		admin = user(4L, "admin@example.com", Role.ADMIN);

		project = new Project();
		project.setId(PROJECT_ID);
		project.setName("Website");
		project.setOwner(owner);
		project.addMember(owner);
		project.addMember(member);
	}

	// --- create / read ---

	@Test
	void createMakesTheCallerOwnerAndMember() {
		actingAs(owner);
		when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ProjectResponse response = projectService.create(new ProjectRequest("  Mobile app ", "  "), owner.getEmail());

		ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
		verify(projectRepository).save(saved.capture());
		assertThat(saved.getValue().getOwner()).isSameAs(owner);
		assertThat(saved.getValue().getMembers()).extracting(ProjectMember::getUser).containsExactly(owner);
		assertThat(saved.getValue().getDescription()).isNull(); // blank description is stored as null
		assertThat(response.name()).isEqualTo("Mobile app");
		assertThat(response.owner().email()).isEqualTo("owner@example.com");
	}

	@Test
	void memberCanSeeProject() {
		actingAs(member);
		projectVisibleTo(member);

		assertThat(projectService.get(PROJECT_ID, member.getEmail()).name()).isEqualTo("Website");
	}

	@Test
	void outsiderGetsNotFoundInsteadOfForbidden() {
		actingAs(outsider);
		projectHiddenFrom(outsider);

		assertThatThrownBy(() -> projectService.get(PROJECT_ID, outsider.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void outsiderCannotListMembers() {
		actingAs(outsider);
		when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, outsider.getId())).thenReturn(false);

		assertThatThrownBy(() -> projectService.listMembers(PROJECT_ID, outsider.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	// --- update ---

	@Test
	void ownerCanEditProject() {
		actingAs(owner);
		projectVisibleTo(owner);

		ProjectResponse response = projectService.update(PROJECT_ID, new ProjectRequest("Website v2", "New design"), owner.getEmail());

		assertThat(project.getName()).isEqualTo("Website v2");
		assertThat(project.getDescription()).isEqualTo("New design");
		assertThat(response.name()).isEqualTo("Website v2");
	}

	@Test
	void memberCannotEditProject() {
		actingAs(member);
		projectVisibleTo(member);

		assertThatThrownBy(() -> projectService.update(PROJECT_ID, new ProjectRequest("Hacked", null), member.getEmail()))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(project.getName()).isEqualTo("Website");
	}

	// --- delete ---

	@Test
	void ownerCanDeleteProject() {
		actingAs(owner);
		projectVisibleTo(owner);

		projectService.delete(PROJECT_ID, owner.getEmail());

		verify(projectRepository).delete(project);
	}

	@Test
	void adminCanDeleteAnyProjectEvenWithoutBeingAMember() {
		actingAs(admin);
		when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

		projectService.delete(PROJECT_ID, admin.getEmail());

		verify(projectRepository).delete(project);
	}

	@Test
	void memberCannotDeleteProject() {
		actingAs(member);
		projectVisibleTo(member);

		assertThatThrownBy(() -> projectService.delete(PROJECT_ID, member.getEmail()))
				.isInstanceOf(AccessDeniedException.class);
		verify(projectRepository, never()).delete(any());
	}

	@Test
	void outsiderCannotDeleteProject() {
		actingAs(outsider);
		projectHiddenFrom(outsider);

		assertThatThrownBy(() -> projectService.delete(PROJECT_ID, outsider.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
		verify(projectRepository, never()).delete(any());
	}

	// --- add member ---

	@Test
	void ownerCanAddMemberByEmail() {
		actingAs(owner);
		projectVisibleTo(owner);
		when(userRepository.findByEmail("outsider@example.com")).thenReturn(Optional.of(outsider));
		when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, outsider.getId())).thenReturn(false);

		ProjectMemberResponse response = projectService.addMember(PROJECT_ID, new AddMemberRequest(" Outsider@Example.com "), owner.getEmail());

		assertThat(response.email()).isEqualTo("outsider@example.com");
		assertThat(project.getMembers()).extracting(ProjectMember::getUser).contains(outsider);
		verify(projectRepository).flush();
	}

	@Test
	void memberCannotAddMembers() {
		actingAs(member);
		projectVisibleTo(member);

		assertThatThrownBy(() -> projectService.addMember(PROJECT_ID, new AddMemberRequest("outsider@example.com"), member.getEmail()))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void addingUnknownEmailIsNotFound() {
		actingAs(owner);
		projectVisibleTo(owner);
		when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> projectService.addMember(PROJECT_ID, new AddMemberRequest("nobody@example.com"), owner.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void addingExistingMemberIsConflict() {
		actingAs(owner);
		projectVisibleTo(owner);
		when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(member));
		when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, member.getId())).thenReturn(true);

		assertThatThrownBy(() -> projectService.addMember(PROJECT_ID, new AddMemberRequest("member@example.com"), owner.getEmail()))
				.isInstanceOf(ConflictException.class);
	}

	// --- remove member ---

	@Test
	void ownerCanRemoveMember() {
		actingAs(owner);
		projectVisibleTo(owner);
		when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, member.getId())).thenReturn(true);

		projectService.removeMember(PROJECT_ID, member.getId(), owner.getEmail());

		assertThat(project.getMembers()).extracting(ProjectMember::getUser).containsExactly(owner);
		verify(taskRepository).unassignAllInProject(PROJECT_ID, member.getId());
	}

	@Test
	void memberCanLeaveProject() {
		actingAs(member);
		projectVisibleTo(member);
		when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, member.getId())).thenReturn(true);

		projectService.removeMember(PROJECT_ID, member.getId(), member.getEmail());

		assertThat(project.getMembers()).extracting(ProjectMember::getUser).containsExactly(owner);
		verify(taskRepository).unassignAllInProject(PROJECT_ID, member.getId());
	}

	@Test
	void memberCannotRemoveSomeoneElse() {
		User secondMember = user(5L, "second@example.com", Role.MEMBER);
		project.addMember(secondMember);
		actingAs(member);
		projectVisibleTo(member);

		assertThatThrownBy(() -> projectService.removeMember(PROJECT_ID, secondMember.getId(), member.getEmail()))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(project.getMembers()).extracting(ProjectMember::getUser).contains(secondMember);
		verify(taskRepository, never()).unassignAllInProject(any(), any());
	}

	@Test
	void adminCanRemoveMember() {
		actingAs(admin);
		when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
		when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, member.getId())).thenReturn(true);

		projectService.removeMember(PROJECT_ID, member.getId(), admin.getEmail());

		assertThat(project.getMembers()).extracting(ProjectMember::getUser).containsExactly(owner);
	}

	@Test
	void ownerCannotBeRemoved() {
		actingAs(owner);
		projectVisibleTo(owner);

		assertThatThrownBy(() -> projectService.removeMember(PROJECT_ID, owner.getId(), owner.getEmail()))
				.isInstanceOf(ConflictException.class);
		verify(taskRepository, never()).unassignAllInProject(any(), any());
	}

	@Test
	void removingNonMemberIsNotFound() {
		actingAs(owner);
		projectVisibleTo(owner);
		when(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, outsider.getId())).thenReturn(false);

		assertThatThrownBy(() -> projectService.removeMember(PROJECT_ID, outsider.getId(), owner.getEmail()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	// --- helpers ---

	private void actingAs(User user) {
		when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
	}

	private void projectVisibleTo(User user) {
		when(projectRepository.findByIdAndMembersUserId(PROJECT_ID, user.getId())).thenReturn(Optional.of(project));
	}

	private void projectHiddenFrom(User user) {
		when(projectRepository.findByIdAndMembersUserId(PROJECT_ID, user.getId())).thenReturn(Optional.empty());
	}

	private static User user(Long id, String email, Role role) {
		User user = new User();
		user.setId(id);
		user.setEmail(email);
		user.setName(email.substring(0, email.indexOf('@')));
		user.setPasswordHash("hash");
		user.setRole(role);
		return user;
	}
}
