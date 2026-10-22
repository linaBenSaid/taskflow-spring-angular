package com.taskflow.dto;

// Returned by register and login. The client sends the token back as "Authorization: Bearer <token>".
public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserResponse user) {

	public static AuthResponse bearer(String token, long expiresInSeconds, UserResponse user) {
		return new AuthResponse(token, "Bearer", expiresInSeconds, user);
	}
}
