package com.taskflow.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

// Turns exceptions into consistent JSON errors (RFC 9457 "problem details").
// Extending ResponseEntityExceptionHandler means Spring's own errors (bad JSON, wrong HTTP method, ...)
// use the same format, and lets us customize the validation error below.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(EmailAlreadyUsedException.class)
	public ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setTitle("Email already in use");
		return problem;
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
		problem.setTitle("Invalid credentials");
		return problem;
	}

	// @Valid failed: return every invalid field with its message.
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}

		ProblemDetail problem = ex.getBody();
		problem.setTitle("Validation failed");
		problem.setDetail("One or more fields are invalid");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}
}
