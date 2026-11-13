package com.garage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.garage.domain.StockMovementType;
import com.garage.dto.PageResponse;
import com.garage.dto.PartDtos.PartRequest;
import com.garage.dto.PartDtos.PartResponse;
import com.garage.dto.PartDtos.StockAdjustRequest;
import com.garage.dto.PartDtos.StockMovementResponse;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.GlobalExceptionHandler;
import com.garage.exception.NotFoundException;
import com.garage.service.PartService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * HTTP contract of /api/parts: routes, status codes, validation and error mapping.
 * Standalone MockMvc, so Spring Security is not loaded: the ADMIN/EMPLOYEE restriction
 * (@PreAuthorize) is NOT covered here, it needs a full-context test such as RoleAccessIT.
 */
@ExtendWith(MockitoExtension.class)
class PartControllerTest {

    @Mock PartService partService;

    MockMvc mvc;
    UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new PartController(partService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private PartResponse response(int stock) {
        return new PartResponse(id, "BRK-001", "Plaquettes", new BigDecimal("45.00"), stock, 3, 4, stock <= 3, true,
                Instant.parse("2026-01-01T10:00:00Z"));
    }

    private static final String VALID_PART =
            "{\"reference\":\"BRK-001\",\"name\":\"Plaquettes\",\"unitPrice\":45.00,\"lowStockThreshold\":3}";

    // ---------- GET ----------

    @Test
    void searchUsesDefaultsAndReturnsThePage() throws Exception {
        when(partService.search(isNull(), isNull(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(response(10)), 0, 10, 1, 1));
        mvc.perform(get("/api/parts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value("BRK-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(partService).search(null, null, false, 0, 10);
    }

    @Test
    void searchPassesFiltersToTheService() throws Exception {
        when(partService.search(any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 1, 25, 0, 0));
        mvc.perform(get("/api/parts").param("q", "brk").param("active", "true").param("lowStock", "true")
                        .param("page", "1").param("size", "25"))
                .andExpect(status().isOk());
        verify(partService).search("brk", true, true, 1, 25);
    }

    @Test
    void getReturnsLowStockFlag() throws Exception {
        when(partService.get(id)).thenReturn(response(2));
        mvc.perform(get("/api/parts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityInStock").value(2))
                .andExpect(jsonPath("$.lowStock").value(true));
    }

    @Test
    void getUnknownPartIs404() throws Exception {
        when(partService.get(id)).thenThrow(new NotFoundException("Part not found"));
        mvc.perform(get("/api/parts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Part not found"));
    }

    @Test
    void invalidIdIs400() throws Exception {
        mvc.perform(get("/api/parts/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void movementsReturnsTheJournal() throws Exception {
        var movement = new StockMovementResponse(UUID.randomUUID(), StockMovementType.OUT, 4, "Atelier",
                Instant.parse("2026-01-02T10:00:00Z"));
        when(partService.movements(id, 0, 10)).thenReturn(new PageResponse<>(List.of(movement), 0, 10, 1, 1));
        mvc.perform(get("/api/parts/{id}/movements", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("OUT"))
                .andExpect(jsonPath("$.content[0].quantity").value(4));
    }

    // ---------- POST create ----------

    @Test
    void createReturns201() throws Exception {
        when(partService.create(any(PartRequest.class))).thenReturn(response(0));
        mvc.perform(post("/api/parts").contentType(MediaType.APPLICATION_JSON).content(VALID_PART))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("BRK-001"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createWithInvalidBodyIs400AndNeverCallsTheService() throws Exception {
        String invalid = "{\"reference\":\"\",\"name\":\"\",\"unitPrice\":-1,\"lowStockThreshold\":-1}";
        mvc.perform(post("/api/parts").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reference").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.unitPrice").exists())
                .andExpect(jsonPath("$.fieldErrors.lowStockThreshold").exists());
        verify(partService, never()).create(any());
    }

    @Test
    void createWithMissingPriceIs400() throws Exception {
        String body = "{\"reference\":\"A\",\"name\":\"B\",\"lowStockThreshold\":0}";
        mvc.perform(post("/api/parts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.unitPrice").exists());
    }

    @Test
    void createWithTooLongReferenceIs400() throws Exception {
        String body = "{\"reference\":\"" + "A".repeat(41) + "\",\"name\":\"B\",\"unitPrice\":1,\"lowStockThreshold\":0}";
        mvc.perform(post("/api/parts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reference").exists());
    }

    @Test
    void createDuplicateReferenceIs409() throws Exception {
        when(partService.create(any())).thenThrow(new ConflictException("A part with this reference already exists"));
        mvc.perform(post("/api/parts").contentType(MediaType.APPLICATION_JSON).content(VALID_PART))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/parts").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST stock ----------

    @Test
    void adjustStockReturnsTheUpdatedPart() throws Exception {
        when(partService.adjustStock(eq(id), any(StockAdjustRequest.class))).thenReturn(response(15));
        mvc.perform(post("/api/parts/{id}/stock", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IN\",\"quantity\":5,\"reason\":\"Livraison\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityInStock").value(15));
        verify(partService).adjustStock(id, new StockAdjustRequest(StockMovementType.IN, 5, "Livraison"));
    }

    @Test
    void adjustStockWithoutTypeIs400() throws Exception {
        mvc.perform(post("/api/parts/{id}/stock", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.type").exists());
        verify(partService, never()).adjustStock(any(), any());
    }

    @Test
    void adjustStockWithUnknownTypeIs400() throws Exception {
        mvc.perform(post("/api/parts/{id}/stock", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"STEAL\",\"quantity\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjustStockBelowZeroIs400WithBusinessMessage() throws Exception {
        when(partService.adjustStock(eq(id), any())).thenThrow(new BusinessException("Cannot reduce stock below zero"));
        mvc.perform(post("/api/parts/{id}/stock", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OUT\",\"quantity\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot reduce stock below zero"));
    }

    @Test
    void concurrentModificationIs409() throws Exception {
        when(partService.adjustStock(eq(id), any())).thenThrow(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(com.garage.domain.Part.class, id));
        mvc.perform(post("/api/parts/{id}/stock", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"IN\",\"quantity\":1}"))
                .andExpect(status().isConflict());
    }

    // ---------- PUT / DELETE / activate ----------

    @Test
    void updateReturnsThePart() throws Exception {
        when(partService.update(eq(id), any(PartRequest.class))).thenReturn(response(10));
        mvc.perform(put("/api/parts/{id}", id).contentType(MediaType.APPLICATION_JSON).content(VALID_PART))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Plaquettes"));
    }

    @Test
    void updateWithInvalidBodyIs400() throws Exception {
        mvc.perform(put("/api/parts/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"\",\"name\":\"x\",\"unitPrice\":1,\"lowStockThreshold\":0}"))
                .andExpect(status().isBadRequest());
        verify(partService, never()).update(any(), any());
    }

    @Test
    void deleteDeactivatesInsteadOfDeleting() throws Exception {
        when(partService.setActive(id, false)).thenReturn(
                new PartResponse(id, "BRK-001", "Plaquettes", BigDecimal.ONE, 10, 3, 4, false, false, Instant.now()));
        mvc.perform(delete("/api/parts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void activateReactivates() throws Exception {
        when(partService.setActive(id, true)).thenReturn(response(10));
        mvc.perform(post("/api/parts/{id}/activate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }
}
