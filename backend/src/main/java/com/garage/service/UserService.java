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
import com.garage.mapper.MapperSupport;
import com.garage.mapper.UserMapper;
import com.garage.repository.ClientRepository;
import com.garage.repository.UserRepository;
import com.garage.security.CurrentUser;
import com.garage.specification.UserSpecifications;
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
    private final UserMapper mapper;

    public UserService(UserRepository userRepository, ClientRepository clientRepository, CurrentUser currentUser,
                       PasswordEncoder passwordEncoder, UserMapper mapper) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String q, Role role, int page, int size) {
        Specification<User> spec = Specification.where(UserSpecifications.matchesText(q))
                .and(UserSpecifications.hasRole(role));
        var pageable = PageUtil.of(page, size, Sort.by("lastName", "firstName"));
        return PageResponse.from(userRepository.findAll(spec, pageable), mapper::toResponse);
    }

    /** Account created by an administrator (any role); the password is stored hashed and never returned. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(MapperSupport.normalizeEmail(request.email()))) {
            throw new ConflictException("Email already registered");
        }
        User user = mapper.toEntity(request, passwordEncoder.encode(request.password()));
        return mapper.toResponse(userRepository.save(user));
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
        mapper.update(request, user);
        if (request.role() != Role.CLIENT) {
            user.setClient(null);
        }
        return mapper.toResponse(user);
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
        return mapper.toResponse(user);
    }

    /** Manual link between a CLIENT account and a client record (1 to 1). A null clientId unlinks. */
    @Transactional
    public UserResponse linkClient(UUID userId, UUID clientId) {
        User user = find(userId);
        if (clientId == null) {
            user.setClient(null);
            return mapper.toResponse(user);
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
        return mapper.toResponse(user);
    }

    private User find(UUID id) {
        return userRepository.findWithClientById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
