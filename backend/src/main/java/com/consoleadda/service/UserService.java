package com.consoleadda.service;

import com.consoleadda.dto.user.UserResponse;
import com.consoleadda.mapper.UserMapper;
import com.consoleadda.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public List<UserResponse> list() {
        return userRepository.findAllByOrderByUsernameAsc().stream().map(userMapper::toResponse).toList();
    }
}
