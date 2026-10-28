package com.garage.service;

import com.garage.domain.Client;
import com.garage.domain.Role;
import com.garage.domain.User;
import com.garage.dto.PageResponse;
import com.garage.dto.UserDtos.CreateUserRequest;
import com.garage.dto.UserDtos.UpdateUserRequest;
import com.garage.dto.UserDtos.UserResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.repository.ClientRepository;
import com.garage.repository.UserRepository;
import com.garage.security.CurrentUser;
import com.garage.util.PageUtil;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, ClientRepository clientRepository, CurrentUser currentUser,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String q, Role role, int page, int size) {
        Specification<User> spec = Specification.where(null);
        if (q != null && !q.isBlank()) {
            String like = PageUtil.likeTerm(q);
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), like, '\\'),
                    cb.like(cb.lower(root.get("lastName")), like, '\\'),
                    cb.like(cb.lower(root.get("firstName")), like, '\\')));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        var pageable = PageUtil.of(page, size, Sort.by("lastName", "firstName"));
        return PageResponse.from(userRepository.findAll(spec, pageable), this::toResponse);
    }

    /** Account created by an administrator (any role); the password is stored hashed and never returned. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(request.role());
        user.setEnabled(true);
        return toResponse(userRepository.save(user));
    }

    /**
     * Name, role and activation. An administrator cannot deactivate their own account nor remove
     * their own ADMIN role. Leaving the CLIENT role drops the client record link.
     */
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = find(id);
        if (user.getId().equals(currentUser.get().id())) {
            if (request.role() != Role.ADMIN) {
                throw new BusinessException("You cannot remove your own administrator role");
            }
            if (!request.enabled()) {
                throw new BusinessException("You cannot deactivate your own account");
            }
        }
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        if (request.role() != Role.CLIENT) {
            user.setClient(null);
        }
        return toResponse(user);
    }

    @Transactional
    public UserResponse changeRole(UUID id, Role role) {
        User user = find(id);
        if (user.getId().equals(currentUser.get().id()) && role != Role.ADMIN) {
            throw new BusinessException("You cannot remove your own administrator role");
        }
        user.setRole(role);
        if (role != Role.CLIENT) {
            user.setClient(null);
        }
        return toResponse(user);
    }

    /** Manual link between a CLIENT account and a client record (1 to 1). A null clientId unlinks. */
    @Transactional
    public UserResponse linkClient(UUID userId, UUID clientId) {
        User user = find(userId);
        if (clientId == null) {
            user.setClient(null);
            return toResponse(user);
        }
        if (user.getRole() != Role.CLIENT) {
            throw new BusinessException("Only CLIENT accounts can be linked to a client record");
        }
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new NotFoundException("Client not found"));
        if (userRepository.existsByClientId(clientId)
                && (user.getClient() == null || !user.getClient().getId().equals(clientId))) {
            throw new ConflictException("This client record is already linked to another account");
        }
        user.setClient(client);
        return toResponse(user);
    }

    private User find(UUID id) {
        return userRepository.findWithClientById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getRole(),
                u.getLanguage(), u.isEnabled(), u.getClient() == null ? null : u.getClient().getId());
    }
}
