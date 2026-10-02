package com.consoleadda.controller;

import com.consoleadda.dto.auth.ChangePasswordRequest;
import com.consoleadda.dto.auth.LoginRequest;
import com.consoleadda.dto.auth.LoginResponse;
import com.consoleadda.dto.auth.UserInfo;
import com.consoleadda.mapper.UserMapper;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;

    public AuthController(AuthService authService, UserMapper userMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest.getRemoteAddr());
    }

    @GetMapping("/me")
    public UserInfo me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userMapper.toInfo(user);
    }

    @PostMapping("/change-password")
    public LoginResponse changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(user, request);
    }
}
