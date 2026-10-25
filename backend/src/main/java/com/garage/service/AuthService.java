package com.garage.service;

import com.garage.config.AppProperties;
import com.garage.domain.Language;
import com.garage.domain.PasswordResetToken;
import com.garage.domain.Role;
import com.garage.domain.User;
import com.garage.dto.AuthDtos.ForgotPasswordRequest;
import com.garage.dto.AuthDtos.LoginRequest;
import com.garage.dto.AuthDtos.MeResponse;
import com.garage.dto.AuthDtos.RegisterRequest;
import com.garage.dto.AuthDtos.ResetPasswordRequest;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.repository.PasswordResetTokenRepository;
import com.garage.repository.UserRepository;
import com.garage.security.AuthUser;
import com.garage.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AppProperties props;
    private final CurrentUser currentUser;

    public AuthService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder, MailService mailService, AppProperties props,
                       CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.props = props;
        this.currentUser = currentUser;
    }

    /** Free sign-up always creates the least privileged role; only an admin can promote the account. */
    @Transactional
    public void register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(Role.CLIENT);
        user.setLanguage(request.language() == null ? Language.FR : request.language());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(LoginRequest request) {
        User user = userRepository.findWithClientByEmail(request.email().trim()).orElse(null);
        if (user == null || !user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return user;
    }

    /** Always silent: the caller gets the same answer whether or not the e-mail exists. */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(User::isEnabled)
                .ifPresent(user -> {
                    byte[] bytes = new byte[32];
                    RANDOM.nextBytes(bytes);
                    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                    PasswordResetToken token = new PasswordResetToken();
                    token.setUser(user);
                    token.setTokenHash(hash(rawToken));
                    token.setExpiresAt(Instant.now().plus(props.resetTokenMinutes(), ChronoUnit.MINUTES));
                    tokenRepository.save(token);
                    mailService.sendPasswordReset(user.getEmail(), user.getLanguage(), rawToken);
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(request.token()))
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessException("Invalid or expired token"));
        token.setUsed(true);
        token.getUser().setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AuthUser auth = currentUser.get();
        User user = userRepository.findWithClientById(auth.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return new MeResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole(), user.getLanguage(), auth.clientId(), auth.clientId() != null);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
