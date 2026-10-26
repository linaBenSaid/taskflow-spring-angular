package com.taskflow.exception;

// Answered with 400: the request is well-formed but breaks a business rule
// (e.g. assigning a task to someone who isn't in the project).
public class InvalidRequestException extends RuntimeException {

	public InvalidRequestException(String message) {
		super(message);
	}
}
