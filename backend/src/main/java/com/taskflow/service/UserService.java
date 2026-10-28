package com.taskflow.service;

import com.taskflow.dto.UserResponse;
import com.taskflow.mapper.UserMapper;
import com.taskflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;

	@Transactional(readOnly = true)
	public UserResponse getByEmail(String email) {
		return userRepository.findByEmail(email)
				.map(userMapper::toResponse)
				.orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
	}
}
