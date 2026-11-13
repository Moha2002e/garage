package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.Quote;
import com.garage.domain.QuoteLine;
import com.garage.domain.QuoteStatus;
import com.garage.dto.QuoteDtos.QuoteLineRequest;
import com.garage.dto.QuoteDtos.QuoteRequest;
import com.garage.exception.BusinessException;
import com.garage.exception.NotFoundException;
import com.garage.repository.QuoteRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.garage.mapper.QuoteMapper;
import org.mapstruct.factory.Mappers;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuoteServiceTest {

    @Mock QuoteRepository quoteRepository;
    @Mock AppointmentService appointmentService;
    @Mock PartService partService;

    QuoteService service;
    Appointment appointment;

    @BeforeEach
    void setUp() {
        service = new QuoteService(quoteRepository, appointmentService, Mappers.getMapper(QuoteMapper.class),
                partService, new QuoteStockService(partService));
        appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setStatus(AppointmentStatus.PLANNED);
        when(appointmentService.findAccessible(appointment.getId())).thenReturn(appointment);
        when(quoteRepository.findWithLinesByAppointmentId(appointment.getId())).thenReturn(Optional.empty());
        when(quoteRepository.saveAndFlush(any(Quote.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static QuoteLineRequest line(String description, String quantity, String unitPrice) {
        return new QuoteLineRequest(null, description, new BigDecimal(quantity), new BigDecimal(unitPrice));
    }

    private Quote existingQuote(QuoteStatus status) {
        Quote quote = new Quote();
        quote.setId(UUID.randomUUID());
        quote.setAppointment(appointment);
        quote.setStatus(status);
        QuoteLine old = new QuoteLine();
        old.setQuote(quote);
        old.setDescription("Old");
        old.setQuantity(BigDecimal.ONE);
        old.setUnitPrice(BigDecimal.TEN);
        quote.getLines().add(old);
        quote.setTotal(new BigDecimal("10.00"));
        when(quoteRepository.findWithLinesByAppointmentId(appointment.getId())).thenReturn(Optional.of(quote));
        return quote;
    }

    @Test
    void totalIsTheSumOfQuantityTimesUnitPrice() {
        var response = service.replaceLines(appointment.getId(), new QuoteRequest(List.of(
                line("Brake pads", "2", "45.50"),
                line("Labour", "1.5", "60"),
                line("Oil", "0.25", "10.10"))));
        // 91.00 + 90.00 + 2.525 rounded to 2.53
        assertEquals(new BigDecimal("183.53"), response.total());
        assertEquals(3, response.lines().size());
        assertEquals(new BigDecimal("91.00"), response.lines().get(0).lineTotal());
        assertEquals(new BigDecimal("2.53"), response.lines().get(2).lineTotal());
        assertEquals(QuoteStatus.DRAFT, response.status());
    }

    @Test
    void zeroPriceLineIsAllowedAndTotalKeepsTwoDecimals() {
        var response = service.replaceLines(appointment.getId(),
                new QuoteRequest(List.of(line("Courtesy check", "1", "0"))));
        assertEquals(new BigDecimal("0.00"), response.total());
        assertEquals(2, response.total().scale());
    }

    @Test
    void replacingKeepsOldLinesButDeactivatesThem() {
        Quote quote = existingQuote(QuoteStatus.DRAFT);
        var response = service.replaceLines(appointment.getId(),
                new QuoteRequest(List.of(line("New", "3", "5"))));
        assertEquals(2, quote.getLines().size());
        assertFalse(quote.getLines().get(0).isActive());
        assertEquals(1, response.lines().size());
        assertEquals(new BigDecimal("15.00"), response.total());
    }

    @Test
    void acceptedOrRefusedQuoteCannotBeReplaced() {
        existingQuote(QuoteStatus.ACCEPTED);
        var request = new QuoteRequest(List.of(line("X", "1", "1")));
        assertThrows(BusinessException.class, () -> service.replaceLines(appointment.getId(), request));
        existingQuote(QuoteStatus.REFUSED);
        assertThrows(BusinessException.class, () -> service.replaceLines(appointment.getId(), request));
    }

    @Test
    void cancelledAppointmentRefusesQuoteChanges() {
        appointment.setStatus(AppointmentStatus.CANCELLED);
        var request = new QuoteRequest(List.of(line("X", "1", "1")));
        assertThrows(BusinessException.class, () -> service.replaceLines(appointment.getId(), request));
    }

    @Test
    void draftCanBeAcceptedOrRefused() {
        Quote quote = existingQuote(QuoteStatus.DRAFT);
        assertEquals(QuoteStatus.ACCEPTED, service.changeStatus(appointment.getId(), QuoteStatus.ACCEPTED).status());
        assertEquals(QuoteStatus.ACCEPTED, quote.getStatus());
        existingQuote(QuoteStatus.DRAFT);
        assertEquals(QuoteStatus.REFUSED, service.changeStatus(appointment.getId(), QuoteStatus.REFUSED).status());
    }

    @Test
    void finalQuoteCannotChangeStatusAgain() {
        existingQuote(QuoteStatus.ACCEPTED);
        assertThrows(BusinessException.class, () -> service.changeStatus(appointment.getId(), QuoteStatus.REFUSED));
        existingQuote(QuoteStatus.REFUSED);
        assertThrows(BusinessException.class, () -> service.changeStatus(appointment.getId(), QuoteStatus.ACCEPTED));
    }

    @Test
    void targetStatusDraftIsRejected() {
        existingQuote(QuoteStatus.DRAFT);
        assertThrows(BusinessException.class, () -> service.changeStatus(appointment.getId(), QuoteStatus.DRAFT));
    }

    @Test
    void statusChangeWithoutQuoteIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.changeStatus(appointment.getId(), QuoteStatus.ACCEPTED));
    }

    @Test
    void getReturnsEmptyWhenThereIsNoQuote() {
        assertTrue(service.get(appointment.getId()).isEmpty());
    }

    @Test
    void getReturnsTheActiveLinesOnly() {
        existingQuote(QuoteStatus.DRAFT).getLines().get(0).setActive(false);
        var response = service.get(appointment.getId()).orElseThrow();
        assertTrue(response.lines().isEmpty());
    }

    @Test
    void anotherClientsAppointmentIsNotFound() {
        UUID other = UUID.randomUUID();
        when(appointmentService.findAccessible(other)).thenThrow(new NotFoundException("Appointment not found"));
        assertThrows(NotFoundException.class, () -> service.get(other));
    }

    // ---------- stock reservation ----------

    private com.garage.domain.Part part() {
        var part = new com.garage.domain.Part();
        part.setId(UUID.randomUUID());
        part.setReference("BRK-001");
        when(partService.find(part.getId())).thenReturn(part);
        return part;
    }

    private static QuoteLineRequest partLine(UUID partId, String quantity) {
        return new QuoteLineRequest(partId, "Plaquettes", new BigDecimal(quantity), new BigDecimal("45"));
    }

    private QuoteLine partLineOf(Quote quote, com.garage.domain.Part part, String quantity) {
        QuoteLine existing = new QuoteLine();
        existing.setQuote(quote);
        existing.setPart(part);
        existing.setDescription("Plaquettes");
        existing.setQuantity(new BigDecimal(quantity));
        existing.setUnitPrice(BigDecimal.TEN);
        quote.getLines().add(existing);
        return existing;
    }

    @Test
    void partLineReservesItsQuantity() {
        var part = part();
        var response = service.replaceLines(appointment.getId(),
                new QuoteRequest(List.of(partLine(part.getId(), "2"), line("Labour", "1.5", "60"))));
        org.mockito.Mockito.verify(partService).reserve(org.mockito.ArgumentMatchers.eq(part.getId()), org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.anyString());
        assertEquals(part.getId(), response.lines().get(0).partId());
        assertEquals("BRK-001", response.lines().get(0).partReference());
        assertEquals(null, response.lines().get(1).partId());
    }

    @Test
    void labourLinesNeverTouchTheStock() {
        service.replaceLines(appointment.getId(), new QuoteRequest(List.of(line("Labour", "1.5", "60"))));
        org.mockito.Mockito.verify(partService, org.mockito.Mockito.never()).reserve(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void partQuantityMustBeWhole() {
        var part = part();
        assertThrows(BusinessException.class, () -> service.replaceLines(appointment.getId(),
                new QuoteRequest(List.of(partLine(part.getId(), "1.5")))));
    }

    @Test
    void unknownPartIsRefused() {
        UUID unknown = UUID.randomUUID();
        when(partService.find(unknown)).thenThrow(new NotFoundException("Part not found"));
        assertThrows(NotFoundException.class, () -> service.replaceLines(appointment.getId(),
                new QuoteRequest(List.of(partLine(unknown, "1")))));
    }

    @Test
    void missingStockFailsTheWholeSave() {
        var part = part();
        org.mockito.Mockito.doThrow(new BusinessException("Not enough stock")).when(partService)
                .reserve(any(), org.mockito.ArgumentMatchers.anyInt(), any());
        assertThrows(BusinessException.class, () -> service.replaceLines(appointment.getId(),
                new QuoteRequest(List.of(partLine(part.getId(), "99")))));
        org.mockito.Mockito.verify(quoteRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void increasingTheQuantityReservesOnlyTheDifference() {
        var part = part();
        Quote quote = existingQuote(QuoteStatus.DRAFT);
        partLineOf(quote, part, "2");
        service.replaceLines(appointment.getId(), new QuoteRequest(List.of(partLine(part.getId(), "5"))));
        org.mockito.Mockito.verify(partService).reserve(org.mockito.ArgumentMatchers.eq(part.getId()), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(partService, org.mockito.Mockito.never()).release(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void removingAPartLineGivesTheStockBack() {
        var part = part();
        Quote quote = existingQuote(QuoteStatus.DRAFT);
        partLineOf(quote, part, "2");
        service.replaceLines(appointment.getId(), new QuoteRequest(List.of(line("Labour", "1", "60"))));
        org.mockito.Mockito.verify(partService).release(org.mockito.ArgumentMatchers.eq(part.getId()), org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void sameQuantityTouchesNothing() {
        var part = part();
        Quote quote = existingQuote(QuoteStatus.DRAFT);
        partLineOf(quote, part, "2");
        service.replaceLines(appointment.getId(), new QuoteRequest(List.of(partLine(part.getId(), "2"))));
        org.mockito.Mockito.verifyNoMoreInteractions(org.mockito.Mockito.ignoreStubs(partService));
    }

    @Test
    void refusingTheQuoteGivesTheStockBack() {
        var part = part();
        Quote quote = existingQuote(QuoteStatus.DRAFT);
        partLineOf(quote, part, "3");
        service.changeStatus(appointment.getId(), QuoteStatus.REFUSED);
        org.mockito.Mockito.verify(partService).release(org.mockito.ArgumentMatchers.eq(part.getId()), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void acceptingTheQuoteKeepsTheStockReserved() {
        var part = part();
        Quote quote = existingQuote(QuoteStatus.DRAFT);
        partLineOf(quote, part, "3");
        service.changeStatus(appointment.getId(), QuoteStatus.ACCEPTED);
        org.mockito.Mockito.verify(partService, org.mockito.Mockito.never()).release(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void refusingTheQuoteOfACancelledAppointmentDoesNotReleaseTwice() {
        var part = part();
        Quote quote = existingQuote(QuoteStatus.DRAFT);
        partLineOf(quote, part, "3");
        appointment.setStatus(AppointmentStatus.CANCELLED);
        service.changeStatus(appointment.getId(), QuoteStatus.REFUSED);
        org.mockito.Mockito.verify(partService, org.mockito.Mockito.never()).release(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }
}
