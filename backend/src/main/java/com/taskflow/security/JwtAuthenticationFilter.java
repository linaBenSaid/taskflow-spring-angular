package com.taskflow.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Runs once per request: if there is a valid "Authorization: Bearer <token>" header,
// marks the request as coming from that user. Otherwise the request continues anonymously
// and SecurityConfig decides whether the endpoint needs a login.
//
// Not a @Component on purpose: Spring Boot would also register it as a plain servlet filter.
// SecurityConfig creates it and adds it to the security filter chain only.
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = header.substring(BEARER_PREFIX.length());
		try {
			String email = jwtService.extractEmail(token);
			UserDetails user = userDetailsService.loadUserByUsername(email);

			var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
		} catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
			// Bad or expired token, or the user was deleted: stay anonymous.
			SecurityContextHolder.clearContext();
		}

		filterChain.doFilter(request, response);
	}
}
