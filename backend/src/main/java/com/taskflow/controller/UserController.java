package com.taskflow.controller;

import com.taskflow.dto.UserResponse;
import com.taskflow.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

	private final UserService userService;

	// @AuthenticationPrincipal is the user that JwtAuthenticationFilter put in the security context.
	@GetMapping("/me")
	@Operation(summary = "The logged-in user")
	public UserResponse me(@AuthenticationPrincipal UserDetails principal) {
		return userService.getByEmail(principal.getUsername());
	}
}
