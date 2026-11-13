package com.garage.service;

import com.garage.config.AppProperties;
import com.garage.domain.PasswordResetToken;
import com.garage.domain.User;
import com.garage.dto.AuthDtos.ForgotPasswordRequest;
import com.garage.dto.AuthDtos.LoginRequest;
import com.garage.dto.AuthDtos.MeResponse;
import com.garage.dto.AuthDtos.RegisterRequest;
import com.garage.dto.AuthDtos.ResetPasswordRequest;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.mapper.AuthMapper;
import com.garage.mapper.MapperSupport;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    // A bcrypt hash of a random value nobody will ever type: checked against when the e-mail is unknown,
    // so an unknown e-mail takes the same time as a wrong password instead of answering instantly
    // (an instant answer would tell an attacker the e-mail does not exist). Computed once at startup
    // with the same encoder as real passwords, so it is always a format bcrypt accepts.
    private final String dummyHash;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AppProperties props;
    private final CurrentUser currentUser;
    private final AuthMapper mapper;

    public AuthService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder, MailService mailService, AppProperties props,
                       CurrentUser currentUser, AuthMapper mapper) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.props = props;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    /** Free sign-up always creates the least privileged role; only an admin can promote the account. */
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(MapperSupport.normalizeEmail(request.email()))) {
            throw new ConflictException("Email already registered");
        }
        userRepository.save(mapper.toUser(request, passwordEncoder.encode(request.password())));
    }

    /**
     * Every failed attempt is logged (email only, never the password): a flood of these lines for the
     * same email, close together, is the signature of a brute-force or credential-stuffing attack.
     */
    @Transactional(readOnly = true)
    public User authenticate(LoginRequest request) {
        String email = request.email().trim();
        User user = userRepository.findWithClientByEmail(email).orElse(null);
        // Always runs bcrypt, on a real hash or the dummy one: an unknown e-mail must take the same
        // time as a wrong password, otherwise the response time alone reveals which e-mails exist.
        boolean passwordOk = passwordEncoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !user.isEnabled() || !passwordOk) {
            log.warn("Failed login attempt for {}", email);
            throw new BadCredentialsException("Invalid credentials");
        }
        log.info("Successful login for {}", email);
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
                    tokenRepository.save(mapper.toResetToken(user, hash(rawToken),
                            Instant.now().plus(props.resetTokenMinutes(), ChronoUnit.MINUTES)));
                    mailService.sendPasswordReset(user.getEmail(), user.getLanguage(), rawToken);
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hash(request.token()))
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessException("Invalid or expired token"));
        token.setUsed(true);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // Revokes every session opened before this change (a stolen cookie stops working).
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AuthUser auth = currentUser.get();
        User user = userRepository.findWithClientById(auth.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return mapper.toMe(user);
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
