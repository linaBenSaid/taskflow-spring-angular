package com.taskflow.security;

import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

	private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";

	@Test
	void tokenContainsTheUsersEmail() {
		JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));

		String token = jwtService.generateToken(user());

		assertThat(jwtService.extractEmail(token)).isEqualTo("alice@example.com");
	}

	@Test
	void rejectsTokenSignedWithAnotherSecret() {
		JwtService issuer = new JwtService(new JwtProperties("another-secret-that-is-also-32-characters-long", 60));
		String token = issuer.generateToken(user());

		JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));

		assertThatThrownBy(() -> jwtService.extractEmail(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsExpiredToken() {
		// Negative lifetime: the token is already expired when it is created.
		JwtService jwtService = new JwtService(new JwtProperties(SECRET, -1));
		String token = jwtService.generateToken(user());

		assertThatThrownBy(() -> jwtService.extractEmail(token)).isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void rejectsTamperedToken() {
		JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));
		String token = jwtService.generateToken(user());
		// Change one character in the middle of the payload (the part between the two dots).
		int index = (token.indexOf('.') + token.lastIndexOf('.')) / 2;
		char replacement = token.charAt(index) == 'a' ? 'b' : 'a';
		String tampered = token.substring(0, index) + replacement + token.substring(index + 1);

		assertThatThrownBy(() -> jwtService.extractEmail(tampered)).isInstanceOf(JwtException.class);
	}

	@Test
	void refusesToStartWithShortSecret() {
		assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 60)))
				.isInstanceOf(IllegalStateException.class);
	}

	private static User user() {
		User user = new User();
		user.setEmail("alice@example.com");
		user.setRole(Role.MEMBER);
		return user;
	}
}
