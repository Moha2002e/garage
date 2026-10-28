package com.garage.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void clientCannotRescheduleAnAppointment() throws Exception {
        mockMvc.perform(patch("/api/appointments/" + UUID.randomUUID() + "/reschedule").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2031-05-02T09:30:00Z\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCanRescheduleOnceTheRoleCheckPasses() throws Exception {
        // Unknown appointment: not 403, the service answers 404.
        mockMvc.perform(patch("/api/appointments/" + UUID.randomUUID() + "/reschedule").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2031-05-02T09:30:00Z\"}"))
                .andExpect(status().isNotFound());
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

    private static final String QUOTE_BODY = "{\"lines\":[{\"description\":\"Oil\",\"quantity\":1,\"unitPrice\":10}]}";

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientCannotUseVehicleDashboardOrQuoteWriteEndpoints() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/vehicles")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/vehicles/" + id).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/vehicles/" + id + "/reactivate").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/appointments/" + id + "/quote").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(QUOTE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/appointments/" + id + "/quote/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientReadingAnotherAppointmentsQuoteGetsNotFound() throws Exception {
        // The mock client has no linked client record, so every appointment is "somebody else's": 404, not 403.
        mockMvc.perform(get("/api/appointments/" + UUID.randomUUID() + "/quote")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCanListVehiclesAndSeeTheDashboardButNotDeactivateOrReactivate() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/vehicles")).andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/vehicles/" + id).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/vehicles/" + id + "/reactivate").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCanWriteQuotesPastTheRoleCheck() throws Exception {
        // Unknown appointment: the role check passed (not 403) and the service answers 404.
        UUID id = UUID.randomUUID();
        mockMvc.perform(put("/api/appointments/" + id + "/quote").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(QUOTE_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/appointments/" + id + "/quote/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPassesTheRoleCheckOnVehicleDeactivationAndReactivation() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/vehicles/" + id).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/vehicles/" + id + "/reactivate").with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/vehicles?active=true&size=25")).andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidQuoteBodyIsRejectedWithBadRequest() throws Exception {
        mockMvc.perform(put("/api/appointments/" + UUID.randomUUID() + "/quote").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCannotReachNewEndpoints() throws Exception {
        mockMvc.perform(get("/api/vehicles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/appointments/" + UUID.randomUUID() + "/quote")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientCannotReadClientHistoryEndpointsNorManageAccounts() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/clients/" + id)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clients/" + id + "/vehicles")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clients/" + id + "/appointments")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientGetsNotFoundOnAVehicleThatIsNotTheirs() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/vehicles/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/vehicles/" + id + "/appointments")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotCreateNorUpdateAccounts() throws Exception {
        mockMvc.perform(post("/api/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/" + UUID.randomUUID()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAccountCreationIsValidated() throws Exception {
        mockMvc.perform(post("/api/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"a@b.test\","
                                + "\"password\":\"short\",\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isBadRequest());
    }

    // ******************************************
    // ************ Invoices and invoice batch ************
    // ******************************************

    @Test
    void anonymousCannotReachInvoiceEndpoints() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invoices/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invoices/batch/runs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invoices/batch/errors")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/exports/invoices.xls")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientGetsForbiddenOnEveryInvoiceEndpoint() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/invoices")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/invoices/" + id)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/invoices/" + id + "/pay").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/invoices/batch/run").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/invoices/batch/runs")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/invoices/batch/errors")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/exports/invoices.xls")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCanUseInvoicesAndTheBatchButNotTheExport() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/invoices?status=ISSUED&size=25&q=clio")).andExpect(status().isOk());
        // Unknown invoice: the role check passed (not 403) and the service answers 404.
        mockMvc.perform(get("/api/invoices/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/invoices/" + id + "/pay").with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/invoices/batch/runs")).andExpect(status().isOk());
        mockMvc.perform(get("/api/invoices/batch/errors?runId=1")).andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/batch/run").with(csrf())).andExpect(status().isOk());
        mockMvc.perform(get("/api/exports/invoices.xls")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanExportInvoicesAndTooLongSearchIsRejected() throws Exception {
        mockMvc.perform(get("/api/exports/invoices.xls")).andExpect(status().isOk());
        mockMvc.perform(get("/api/invoices?q=" + "a".repeat(101))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/invoices?status=UNKNOWN")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/invoices/batch/run")).andExpect(status().isForbidden());
    }

    // ******************************************
    // ************ Invoice PDF ************
    // ******************************************

    @Test
    void anonymousCannotDownloadAnInvoicePdf() throws Exception {
        mockMvc.perform(get("/api/invoices/" + UUID.randomUUID() + "/pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientCannotDownloadAnInvoicePdf() throws Exception {
        mockMvc.perform(get("/api/invoices/" + UUID.randomUUID() + "/pdf")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeePassesTheRoleCheckOfTheInvoicePdf() throws Exception {
        // Unknown invoice: not 403, the service answers 404.
        mockMvc.perform(get("/api/invoices/" + UUID.randomUUID() + "/pdf")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPassesTheRoleCheckOfTheInvoicePdf() throws Exception {
        mockMvc.perform(get("/api/invoices/" + UUID.randomUUID() + "/pdf")).andExpect(status().isNotFound());
    }
}
