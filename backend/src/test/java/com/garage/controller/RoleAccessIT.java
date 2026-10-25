package com.garage.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.garage.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class RoleAccessIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/appointments")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientCannotUseStaffEndpoints() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/statistics/appointments")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/exports/clients.xls")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/appointments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void unlinkedClientSeesAnEmptyAppointmentList() throws Exception {
        mockMvc.perform(get("/api/appointments")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCanReadButNotDeactivateNorExport() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/clients/" + UUID.randomUUID()).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/exports/appointments.xls")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/statistics/appointments")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanExport() throws Exception {
        mockMvc.perform(get("/api/exports/clients.xls")).andExpect(status().isOk());
        mockMvc.perform(get("/api/exports/appointments.xls")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void writeWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void forgotPasswordAnswersTheSameForUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted());
    }
}
