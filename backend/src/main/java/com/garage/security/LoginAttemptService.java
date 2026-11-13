package com.garage.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory rate limiting on login (and the forgot-password mailer) by IP + email: 5 failures lock the
 * pair for 1 minute, the next 5 for 5 minutes, then 15 minutes. Resets on a successful login.
 * In-memory on purpose (no new table): resets if the app restarts and is per-instance only, which is
 * enough for a single-server deployment; a multi-instance deployment would need a shared store (Redis).
 */
@Service
public class LoginAttemptService {

    private static final int[] FAILURES_BEFORE_LOCK = {5, 10, 15};
    private static final Duration[] LOCK_DURATIONS = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15)};

    private final ConcurrentHashMap<String, State> attempts = new ConcurrentHashMap<>();

    private record State(int failures, Instant lockedUntil) {
    }

    /** Throws if this IP + email pair is currently locked out. */
    public void checkAllowed(HttpServletRequest request, String email) {
        State state = attempts.get(key(request, email));
        if (state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(Instant.now())) {
            long seconds = Duration.between(Instant.now(), state.lockedUntil()).toSeconds() + 1;
            throw new TooManyAttemptsException(seconds);
        }
    }

    /** Call on every failed attempt; locks the pair once a threshold is crossed. */
    public void recordFailure(HttpServletRequest request, String email) {
        attempts.compute(key(request, email), (k, previous) -> {
            int failures = (previous == null ? 0 : previous.failures()) + 1;
            Duration lock = null;
            for (int i = FAILURES_BEFORE_LOCK.length - 1; i >= 0; i--) {
                if (failures >= FAILURES_BEFORE_LOCK[i]) {
                    lock = LOCK_DURATIONS[i];
                    break;
                }
            }
            return new State(failures, lock == null ? null : Instant.now().plus(lock));
        });
    }

    /** Call on a successful login: the pair starts clean again. */
    public void recordSuccess(HttpServletRequest request, String email) {
        attempts.remove(key(request, email));
    }

    private String key(HttpServletRequest request, String email) {
        return clientIp(request) + '|' + email.trim().toLowerCase(Locale.ROOT);
    }

    /** Best-effort client IP: the first entry of X-Forwarded-For (reverse proxy) or the socket address. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
