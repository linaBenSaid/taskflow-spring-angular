package com.taskflow.exception;

// Answered with 404. Also used when the caller isn't allowed to know that something exists.
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

	public static ResourceNotFoundException project(Long projectId) {
		return new ResourceNotFoundException("Project " + projectId + " not found");
	}
}
