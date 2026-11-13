package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garage.domain.Part;
import com.garage.domain.StockMovement;
import com.garage.domain.StockMovementType;
import com.garage.dto.PartDtos.PartRequest;
import com.garage.dto.PartDtos.StockAdjustRequest;
import com.garage.exception.BusinessException;
import com.garage.exception.ConflictException;
import com.garage.exception.NotFoundException;
import com.garage.mapper.PartMapper;
import com.garage.repository.PartRepository;
import com.garage.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartServiceTest {

    @Mock PartRepository partRepository;
    @Mock StockMovementRepository movementRepository;

    PartService service;
    Part part;

    @BeforeEach
    void setUp() {
        service = new PartService(partRepository, movementRepository, Mappers.getMapper(PartMapper.class));
        part = new Part();
        part.setId(UUID.randomUUID());
        part.setReference("BRK-001");
        part.setName("Plaquettes");
        part.setUnitPrice(new BigDecimal("45.00"));
        part.setQuantityInStock(10);
        part.setLowStockThreshold(3);
        when(partRepository.findById(part.getId())).thenReturn(Optional.of(part));
        when(partRepository.save(any(Part.class))).thenAnswer(i -> i.getArgument(0));
    }

    private PartRequest request(String reference, String price) {
        return new PartRequest(reference, "  Filtre  ", new BigDecimal(price), 2, 4);
    }

    // ---------- create ----------

    @Test
    void createNormalizesReferenceAndStartsWithEmptyActiveStock() {
        var response = service.create(request(" flt-9 ", "12.50"));
        assertEquals("FLT-9", response.reference());
        assertEquals("Filtre", response.name());
        assertEquals(0, response.quantityInStock());
        assertTrue(response.active());
    }

    @Test
    void createRefusesDuplicateReference() {
        when(partRepository.existsByReference("FLT-9")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(request("flt-9", "12.50")));
        verify(partRepository, never()).save(any());
    }

    @Test
    void createRefusesNegativePrice() {
        assertThrows(BusinessException.class, () -> service.create(request("FLT-9", "-1")));
        verify(partRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void updateKeepsStockAndChecksDuplicateOnOtherParts() {
        var response = service.update(part.getId(), request("brk-002", "50"));
        assertEquals("BRK-002", response.reference());
        assertEquals(10, response.quantityInStock());
        verify(partRepository).existsByReferenceAndIdNot("BRK-002", part.getId());
    }

    @Test
    void updateRefusesReferenceUsedByAnotherPart() {
        when(partRepository.existsByReferenceAndIdNot("FLT-9", part.getId())).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.update(part.getId(), request("FLT-9", "5")));
        assertEquals("BRK-001", part.getReference());
    }

    @Test
    void updateUnknownPartIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.update(UUID.randomUUID(), request("X", "5")));
    }

    // ---------- adjustStock ----------

    private StockMovement captureMovement() {
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void inIncreasesStockAndWritesJournal() {
        var response = service.adjustStock(part.getId(), new StockAdjustRequest(StockMovementType.IN, 5, "Livraison"));
        assertEquals(15, response.quantityInStock());
        StockMovement movement = captureMovement();
        assertEquals(StockMovementType.IN, movement.getType());
        assertEquals(5, movement.getQuantity());
        assertEquals("Livraison", movement.getReason());
        assertEquals(part, movement.getPart());
    }

    @Test
    void outDecreasesStock() {
        var response = service.adjustStock(part.getId(), new StockAdjustRequest(StockMovementType.OUT, 4, null));
        assertEquals(6, response.quantityInStock());
        assertEquals(4, captureMovement().getQuantity());
    }

    @Test
    void outCanEmptyTheStockExactly() {
        var response = service.adjustStock(part.getId(), new StockAdjustRequest(StockMovementType.OUT, 10, null));
        assertEquals(0, response.quantityInStock());
    }

    @Test
    void outBeyondStockIsRefused() {
        assertThrows(BusinessException.class,
                () -> service.adjustStock(part.getId(), new StockAdjustRequest(StockMovementType.OUT, 11, null)));
        assertEquals(10, part.getQuantityInStock());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void positiveAdjustmentAddsAndNegativeSubtracts() {
        service.adjustStock(part.getId(), new StockAdjustRequest(StockMovementType.ADJUSTMENT, 3, "Inventaire"));
        assertEquals(13, part.getQuantityInStock());
        service.adjustStock(part.getId(), new StockAdjustRequest(StockMovementType.ADJUSTMENT, -5, "Casse"));
        assertEquals(8, part.getQuantityInStock());
    }

    @Test
    void negativeAdjustmentBelowZeroIsRefused() {
        assertThrows(BusinessException.class,
                () -> service.adjustStock(part.getId(), new StockAdjustRequest(StockMovementType.ADJUSTMENT, -11, null)));
        assertEquals(10, part.getQuantityInStock());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void zeroAdjustmentIsRefused() {
        assertThrows(BusinessException.class,
                () -> service.adjustStock(part.getId(), new StockAdjustRequest(StockMovementType.ADJUSTMENT, 0, null)));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void inAndOutNeedStrictlyPositiveQuantity() {
        for (StockMovementType type : new StockMovementType[] {StockMovementType.IN, StockMovementType.OUT}) {
            assertThrows(BusinessException.class,
                    () -> service.adjustStock(part.getId(), new StockAdjustRequest(type, 0, null)));
            assertThrows(BusinessException.class,
                    () -> service.adjustStock(part.getId(), new StockAdjustRequest(type, -2, null)));
        }
        assertEquals(10, part.getQuantityInStock());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void adjustUnknownPartIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.adjustStock(UUID.randomUUID(),
                new StockAdjustRequest(StockMovementType.IN, 1, null)));
    }

    // ---------- lowStock / setActive ----------

    @Test
    void lowStockWhenAtOrBelowThreshold() {
        assertFalse(service.get(part.getId()).lowStock());
        service.adjustStock(part.getId(), new StockAdjustRequest(StockMovementType.OUT, 7, null));
        assertTrue(service.get(part.getId()).lowStock());
    }

    @Test
    void setActiveTogglesWithoutTouchingStock() {
        assertFalse(service.setActive(part.getId(), false).active());
        assertTrue(service.setActive(part.getId(), true).active());
        assertEquals(10, part.getQuantityInStock());
    }

    // ---------- reserve / release (used by quotes) ----------

    @Test
    void reserveTakesStockOutAndWritesJournal() {
        service.reserve(part.getId(), 4, "Quote - appointment X");
        assertEquals(6, part.getQuantityInStock());
        StockMovement movement = captureMovement();
        assertEquals(StockMovementType.OUT, movement.getType());
        assertEquals("Quote - appointment X", movement.getReason());
    }

    @Test
    void reserveWithMissingStockSaysHowLongTheDeliveryTakes() {
        part.setLeadTimeDays(4);
        var ex = assertThrows(BusinessException.class, () -> service.reserve(part.getId(), 11, "r"));
        assertTrue(ex.getMessage().contains("BRK-001"));
        assertTrue(ex.getMessage().contains("10 available, 11 needed"));
        assertTrue(ex.getMessage().contains("4 days"));
        assertEquals(10, part.getQuantityInStock());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void reserveWithMissingStockAndUnknownLeadTimeStillExplains() {
        var ex = assertThrows(BusinessException.class, () -> service.reserve(part.getId(), 11, "r"));
        assertFalse(ex.getMessage().contains("days"));
    }

    @Test
    void reserveRefusesAnInactivePart() {
        part.setActive(false);
        assertThrows(BusinessException.class, () -> service.reserve(part.getId(), 1, "r"));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void releasePutsStockBack() {
        service.release(part.getId(), 3, "Appointment cancelled");
        assertEquals(13, part.getQuantityInStock());
        assertEquals(StockMovementType.IN, captureMovement().getType());
    }
}
