package com.taskflow.security;

import com.taskflow.entity.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

// Creates and checks signed JWTs (HMAC-SHA). The token's subject is the user's email.
@Service
public class JwtService {

	private static final int MIN_SECRET_BYTES = 32; // HS256 needs a key of at least 256 bits

	private final SecretKey key;
	private final Duration expiration;

	public JwtService(JwtProperties properties) {
		if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " characters long");
		}
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.expiration = Duration.ofMinutes(properties.expirationMinutes());
	}

	public String generateToken(User user) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(user.getEmail())
				.claim("role", user.getRole().name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expiration)))
				.signWith(key)
				.compact();
	}

	/**
	 * Returns the email stored in a valid token.
	 *
	 * @throws JwtException if the token is malformed, expired or signed with another key
	 */
	public String extractEmail(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload()
				.getSubject();
	}

	public long getExpirationSeconds() {
		return expiration.toSeconds();
	}
}
