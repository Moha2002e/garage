package com.garage.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.garage.domain.Role;
import com.garage.dto.UserDtos.CreateUserRequest;
import com.garage.dto.UserDtos.UpdateUserRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** Bean Validation rules of the account contract (the controller applies them with @Valid). */
class UserDtosValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static CreateUserRequest withPassword(String password) {
        return new CreateUserRequest("Jean", "Dupont", "jean@garage.test", password, Role.EMPLOYEE);
    }

    @Test
    void validCreationPasses() {
        assertTrue(validator.validate(withPassword("Abcdefghij12")).isEmpty());
    }

    @Test
    void passwordNeedsAtLeastTwelveCharacters() {
        assertEquals(1, validator.validate(withPassword("Abcdefghi12")).size());
    }

    @Test
    void passwordNeedsAnUpperCaseALowerCaseAndADigit() {
        assertEquals(1, validator.validate(withPassword("abcdefghij12")).size());
        assertEquals(1, validator.validate(withPassword("ABCDEFGHIJ12")).size());
        assertEquals(1, validator.validate(withPassword("Abcdefghijkl")).size());
    }

    @Test
    void emailMustBeValidAndRoleMandatory() {
        assertEquals(1, validator.validate(
                new CreateUserRequest("Jean", "Dupont", "not-an-email", "Abcdefghij12", Role.ADMIN)).size());
        assertEquals(1, validator.validate(
                new CreateUserRequest("Jean", "Dupont", "jean@garage.test", "Abcdefghij12", null)).size());
    }

    @Test
    void updateRequiresNamesRoleAndActivation() {
        assertTrue(validator.validate(new UpdateUserRequest("A", "B", Role.CLIENT, true)).isEmpty());
        assertEquals(1, validator.validate(new UpdateUserRequest(" ", "B", Role.CLIENT, true)).size());
        assertEquals(1, validator.validate(new UpdateUserRequest("A", "B", null, true)).size());
        assertEquals(1, validator.validate(new UpdateUserRequest("A", "B", Role.CLIENT, null)).size());
    }
}
