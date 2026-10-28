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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reads the JWT from the HttpOnly cookie and authenticates the request. Not a Spring bean (registered in SecurityConfig only). */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final SecurityContextRepository CONTEXT_REPOSITORY = new RequestAttributeSecurityContextRepository();

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
            jwtService.parseUserId(token)
                    .flatMap(userRepository::findWithClientById)
                    .filter(User::isEnabled)
                    .ifPresent(user -> {
                        var principal = new AuthUser(user.getId(), user.getEmail(), user.getRole(),
                                user.getClient() == null ? null : user.getClient().getId());
                        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(auth);
                        SecurityContextHolder.setContext(context);
                        // Declare the context to Spring Security (request scope, stateless). Without this,
                        // SessionManagementFilter treats every request as a new login and its CSRF strategy
                        // deletes the XSRF-TOKEN cookie, so one request out of two was refused with a 401.
                        CONTEXT_REPOSITORY.saveContext(context, request, response);
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
