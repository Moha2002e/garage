package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garage.domain.Client;
import com.garage.domain.Role;
import com.garage.domain.User;
import com.garage.dto.UserDtos.CreateUserRequest;
import com.garage.dto.UserDtos.UpdateUserRequest;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.repository.ClientRepository;
import com.garage.repository.UserRepository;
import com.garage.security.AuthUser;
import com.garage.security.CurrentUser;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock ClientRepository clientRepository;
    @Mock CurrentUser currentUser;
    @Mock PasswordEncoder passwordEncoder;

    UserService service;
    UUID adminId;
    User target;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, clientRepository, currentUser, passwordEncoder);
        adminId = UUID.randomUUID();
        when(currentUser.get()).thenReturn(new AuthUser(adminId, "admin@garage.test", Role.ADMIN, null));
        when(passwordEncoder.encode(any())).thenAnswer(i -> "hash:" + i.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        target = new User();
        target.setId(UUID.randomUUID());
        target.setEmail("employee@garage.test");
        target.setFirstName("Eve");
        target.setLastName("Durand");
        target.setRole(Role.EMPLOYEE);
        when(userRepository.findWithClientById(target.getId())).thenReturn(Optional.of(target));
    }

    private CreateUserRequest createRequest(String email) {
        return new CreateUserRequest("  Jean ", " Dupont ", email, "Abcdefghij12", Role.EMPLOYEE);
    }

    @Test
    void creationNormalizesTheEmailHashesThePasswordAndEnablesTheAccount() {
        when(userRepository.existsByEmailIgnoreCase("jean.dupont@garage.test")).thenReturn(false);

        var response = service.create(createRequest("  Jean.Dupont@Garage.test "));

        assertEquals("jean.dupont@garage.test", response.email());
        assertEquals("Jean", response.firstName());
        assertEquals("Dupont", response.lastName());
        assertEquals(Role.EMPLOYEE, response.role());
        assertTrue(response.enabled());
        assertNull(response.clientId());
        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hash:Abcdefghij12", captor.getValue().getPasswordHash());
    }

    @Test
    void creationWithATakenEmailIsAConflict() {
        when(userRepository.existsByEmailIgnoreCase("jean@garage.test")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(createRequest("Jean@garage.test")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateChangesNameRoleAndActivation() {
        var response = service.update(target.getId(), new UpdateUserRequest(" Eva ", " Martin ", Role.ADMIN, false));
        assertEquals("Eva", response.firstName());
        assertEquals("Martin", response.lastName());
        assertEquals(Role.ADMIN, response.role());
        assertFalse(response.enabled());
        assertEquals("employee@garage.test", target.getEmail());
    }

    @Test
    void leavingTheClientRoleDropsTheClientLink() {
        target.setRole(Role.CLIENT);
        target.setClient(new Client());
        service.update(target.getId(), new UpdateUserRequest("Eve", "Durand", Role.EMPLOYEE, true));
        assertNull(target.getClient());
    }

    @Test
    void anAdministratorCannotDeactivateTheirOwnAccount() {
        target.setId(adminId);
        target.setRole(Role.ADMIN);
        when(userRepository.findWithClientById(adminId)).thenReturn(Optional.of(target));
        assertThrows(BusinessException.class,
                () -> service.update(adminId, new UpdateUserRequest("Eve", "Durand", Role.ADMIN, false)));
        assertTrue(target.isEnabled());
    }

    @Test
    void anAdministratorCannotRemoveTheirOwnAdminRole() {
        target.setId(adminId);
        target.setRole(Role.ADMIN);
        when(userRepository.findWithClientById(adminId)).thenReturn(Optional.of(target));
        assertThrows(BusinessException.class,
                () -> service.update(adminId, new UpdateUserRequest("Eve", "Durand", Role.EMPLOYEE, true)));
        assertEquals(Role.ADMIN, target.getRole());
    }

    @Test
    void anAdministratorCanEditTheirOwnNameWhileStayingAdminAndActive() {
        target.setId(adminId);
        target.setRole(Role.ADMIN);
        when(userRepository.findWithClientById(adminId)).thenReturn(Optional.of(target));
        var response = service.update(adminId, new UpdateUserRequest("Nouveau", "Nom", Role.ADMIN, true));
        assertEquals("Nouveau", response.firstName());
        assertEquals(Role.ADMIN, response.role());
    }

    @Test
    void updatingAnUnknownAccountIsNotFound() {
        assertThrows(NotFoundException.class,
                () -> service.update(UUID.randomUUID(), new UpdateUserRequest("A", "B", Role.CLIENT, true)));
    }
}
