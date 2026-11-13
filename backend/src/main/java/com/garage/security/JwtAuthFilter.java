package com.garage.security;

import com.garage.domain.User;
import com.garage.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reads the JWT from the HttpOnly cookie and authenticates the request. Not a Spring bean (registered in SecurityConfig only). */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final String cookieName;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository, String cookieName) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readCookie(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(token)
                    .flatMap(parsed -> userRepository.findWithClientById(parsed.userId())
                            // A mismatched version means the token was revoked (logout-everywhere or password change).
                            .filter(user -> user.getTokenVersion() == parsed.tokenVersion()))
                    .filter(User::isEnabled)
                    .ifPresent(user -> {
                        var principal = new AuthUser(user.getId(), user.getEmail(), user.getRole(),
                                user.getClient() == null ? null : user.getClient().getId());
                        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (cookieName.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
