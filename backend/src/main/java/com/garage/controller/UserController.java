package com.garage.controller;

import com.garage.domain.Role;
import com.garage.dto.PageResponse;
import com.garage.dto.UserDtos.LinkClientRequest;
import com.garage.dto.UserDtos.RoleRequest;
import com.garage.dto.UserDtos.UserResponse;
import com.garage.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User accounts, roles and client linking")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Search user accounts (page size 10 or 25)")
    public PageResponse<UserResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Role role,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return userService.search(q, role, page, size);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change the role of an account")
    public UserResponse changeRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return userService.changeRole(id, request.role());
    }

    @PutMapping("/{id}/client")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Link a CLIENT account to a client record (null clientId unlinks)")
    public UserResponse linkClient(@PathVariable UUID id, @RequestBody LinkClientRequest request) {
        return userService.linkClient(id, request.clientId());
    }
}
