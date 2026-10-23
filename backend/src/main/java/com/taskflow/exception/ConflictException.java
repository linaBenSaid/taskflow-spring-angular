package com.taskflow.exception;

// Answered with 409: the request is valid but clashes with the current state (e.g. "already a member").
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
