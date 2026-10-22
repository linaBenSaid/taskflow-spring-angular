package com.taskflow.service;

import com.taskflow.dto.AuthResponse;
import com.taskflow.dto.LoginRequest;
import com.taskflow.dto.RegisterRequest;
import com.taskflow.dto.UserResponse;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.exception.EmailAlreadyUsedException;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyUsedException(email);
		}

		User user = new User();
		user.setEmail(email);
		user.setName(request.name().trim());
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setRole(Role.MEMBER);

		return toAuthResponse(userRepository.save(user));
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		// Same message for "unknown email" and "wrong password", so attackers can't tell which emails exist.
		User user = userRepository.findByEmail(normalizeEmail(request.email()))
				.filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
				.orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

		return toAuthResponse(user);
	}

	private AuthResponse toAuthResponse(User user) {
		return AuthResponse.bearer(jwtService.generateToken(user), jwtService.getExpirationSeconds(),
				UserResponse.from(user));
	}

	// "Alice@Mail.com " and "alice@mail.com" are the same account.
	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
