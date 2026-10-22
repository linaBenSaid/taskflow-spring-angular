package com.taskflow.service;

import com.taskflow.dto.AuthResponse;
import com.taskflow.dto.LoginRequest;
import com.taskflow.dto.RegisterRequest;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.exception.EmailAlreadyUsedException;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit test: no Spring, no database. Every dependency is a Mockito mock.
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@InjectMocks
	private AuthService authService;

	@Test
	void registerSavesMemberWithHashedPasswordAndReturnsToken() {
		when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
		when(passwordEncoder.encode("secret123")).thenReturn("hashed");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

		AuthResponse response = authService.register(new RegisterRequest(" Alice@Example.com ", "secret123", " Alice "));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
		assertThat(saved.getValue().getName()).isEqualTo("Alice");
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
		assertThat(saved.getValue().getRole()).isEqualTo(Role.MEMBER);

		assertThat(response.token()).isEqualTo("jwt-token");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.user().email()).isEqualTo("alice@example.com");
	}

	@Test
	void registerRejectsEmailThatIsAlreadyUsed() {
		when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(new RegisterRequest("alice@example.com", "secret123", "Alice")))
				.isInstanceOf(EmailAlreadyUsedException.class);

		verify(userRepository, never()).save(any());
	}

	@Test
	void loginReturnsTokenForCorrectPassword() {
		User user = user("alice@example.com", "hashed");
		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
		when(jwtService.generateToken(user)).thenReturn("jwt-token");

		AuthResponse response = authService.login(new LoginRequest("ALICE@example.com", "secret123"));

		assertThat(response.token()).isEqualTo("jwt-token");
	}

	@Test
	void loginRejectsWrongPassword() {
		when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user("alice@example.com", "hashed")));
		when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong-password")))
				.isInstanceOf(BadCredentialsException.class)
				.hasMessage("Invalid email or password");
	}

	@Test
	void loginRejectsUnknownEmailWithSameMessage() {
		when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "secret123")))
				.isInstanceOf(BadCredentialsException.class)
				.hasMessage("Invalid email or password");
	}

	private static User user(String email, String passwordHash) {
		User user = new User();
		user.setEmail(email);
		user.setPasswordHash(passwordHash);
		user.setName("Alice");
		user.setRole(Role.MEMBER);
		return user;
	}
}
