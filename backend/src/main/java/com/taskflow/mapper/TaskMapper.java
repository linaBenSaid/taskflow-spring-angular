package com.taskflow.mapper;

import com.taskflow.dto.TaskResponse;
import com.taskflow.entity.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// assignee (a User, may be null) becomes a UserSummary through UserMapper; MapStruct keeps null as null.
@Mapper(config = MappingConfig.class, uses = UserMapper.class)
public interface TaskMapper {

	// Only the project's id goes into the response. Reading it doesn't load the project from the database.
	@Mapping(target = "projectId", source = "project.id")
	TaskResponse toResponse(Task task);
}
