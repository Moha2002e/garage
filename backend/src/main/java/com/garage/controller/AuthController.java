package com.garage.controller;

import com.garage.config.AppProperties;
import com.garage.domain.User;
import com.garage.dto.AuthDtos.ForgotPasswordRequest;
import com.garage.dto.AuthDtos.LoginRequest;
import com.garage.dto.AuthDtos.MeResponse;
import com.garage.dto.AuthDtos.RegisterRequest;
import com.garage.dto.AuthDtos.ResetPasswordRequest;
import com.garage.mapper.AuthMapper;
import com.garage.security.JwtService;
import com.garage.security.LoginAttemptService;
import com.garage.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, sign-up, logout and password reset")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final AppProperties props;
    private final AuthMapper authMapper;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthService authService, JwtService jwtService, AppProperties props,
                          AuthMapper authMapper, LoginAttemptService loginAttemptService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.props = props;
        this.authMapper = authMapper;
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping("/me")
    @Operation(summary = "Current user")
    public MeResponse me() {
        return authService.me();
    }

    @PostMapping("/login")
    @Operation(summary = "Log in; sets the HttpOnly session cookie")
    public MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                            HttpServletResponse response) {
        loginAttemptService.checkAllowed(httpRequest, request.email());
        User user;
        try {
            user = authService.authenticate(request);
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(httpRequest, request.email());
            throw e;
        }
        loginAttemptService.recordSuccess(httpRequest, request.email());
        String token = jwtService.generate(user.getId(), user.getTokenVersion(), request.rememberMe());
        addCookie(response, token, jwtService.lifetime(request.rememberMe()));
        return authMapper.toMe(user);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Free sign-up (least privileged role)")
    @PreAuthorize("hasRole('ADMIN')")
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Log out; clears the session cookie")
    public void logout(HttpServletResponse response) {
        addCookie(response, "", Duration.ZERO);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a reset link; same answer whether or not the e-mail exists")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a new password with a valid reset token")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(props.cookie().name(), value)
                .httpOnly(true)
                .secure(props.cookie().secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
