package com.taskflow.mapper;

import com.taskflow.dto.UserResponse;
import com.taskflow.dto.UserSummary;
import com.taskflow.entity.User;
import org.mapstruct.Mapper;

// MapStruct writes the implementation (UserMapperImpl) at compile time: fields with the same name are copied.
// passwordHash is never copied, because no DTO has a field with that name.
@Mapper(config = MappingConfig.class)
public interface UserMapper {

	UserResponse toResponse(User user);

	UserSummary toSummary(User user);
}
