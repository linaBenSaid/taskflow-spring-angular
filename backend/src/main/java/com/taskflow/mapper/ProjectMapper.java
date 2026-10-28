package com.taskflow.mapper;

import com.taskflow.dto.ProjectMemberResponse;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// uses = UserMapper: to turn Project.owner (a User) into ProjectResponse.owner (a UserSummary).
@Mapper(config = MappingConfig.class, uses = UserMapper.class)
public interface ProjectMapper {

	ProjectResponse toResponse(Project project);

	// The member's fields come from the nested user, so the names have to be spelled out.
	@Mapping(target = "userId", source = "user.id")
	@Mapping(target = "email", source = "user.email")
	@Mapping(target = "name", source = "user.name")
	ProjectMemberResponse toResponse(ProjectMember member);
}
