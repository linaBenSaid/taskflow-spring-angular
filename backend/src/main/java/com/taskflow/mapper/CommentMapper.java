package com.taskflow.mapper;

import com.taskflow.dto.CommentResponse;
import com.taskflow.entity.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// author (a User, may be null) becomes a UserSummary through UserMapper.
@Mapper(config = MappingConfig.class, uses = UserMapper.class)
public interface CommentMapper {

	@Mapping(target = "taskId", source = "task.id")
	CommentResponse toResponse(Comment comment);
}
