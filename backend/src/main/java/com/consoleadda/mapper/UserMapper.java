package com.consoleadda.mapper;

import com.consoleadda.dto.auth.UserInfo;
import com.consoleadda.dto.user.UserResponse;
import com.consoleadda.entity.User;
import com.consoleadda.security.AuthenticatedUser;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserInfo toInfo(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getFullName(), user.roleCode(),
                user.isMustChangePassword());
    }

    public UserInfo toInfo(AuthenticatedUser user) {
        return new UserInfo(user.id(), user.username(), user.fullName(), user.role(), user.mustChangePassword());
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.roleCode(),
                user.isActive(), user.isMustChangePassword(), user.getLastLoginAt());
    }
}
