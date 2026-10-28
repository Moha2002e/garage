package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garage.domain.AppointmentStatus;
import com.garage.exception.BusinessException;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.QuoteRepository;
import com.garage.repository.VehicleRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatisticsServiceTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock QuoteRepository quoteRepository;
    @Mock VehicleRepository vehicleRepository;

    StatisticsService service;

    @BeforeEach
    void setUp() {
        service = new StatisticsService(appointmentRepository, quoteRepository, vehicleRepository);
    }

    @Test
    void statusDistributionSumsTheMonthsAndAlwaysReturnsFourStatuses() {
        when(appointmentRepository.countByMonthAndStatus(any(), any())).thenReturn(List.of(
                new Object[] {3, "COMPLETED", 5L},
                new Object[] {4, "COMPLETED", 2L},
                new Object[] {4, "CANCELLED", 1L}));

        var result = service.statusDistribution(2031);

        assertEquals(4, result.size());
        assertEquals(AppointmentStatus.PLANNED, result.get(0).status());
        assertEquals(0, result.get(0).count());
        assertEquals(0, result.get(1).count());
        assertEquals(AppointmentStatus.COMPLETED, result.get(2).status());
        assertEquals(7, result.get(2).count());
        assertEquals(AppointmentStatus.CANCELLED, result.get(3).status());
        assertEquals(1, result.get(3).count());
    }

    @Test
    void revenueByMonthReturnsTwelveMonthsWithZeroForEmptyOnes() {
        when(quoteRepository.revenueByMonth(any(), any())).thenReturn(List.of(
                new Object[] {3, new BigDecimal("1250.5")},
                new Object[] {12, new BigDecimal("80.00")}));

        var result = service.revenueByMonth(2031);

        assertEquals(12, result.size());
        assertEquals(1, result.get(0).month());
        assertEquals(new BigDecimal("0.00"), result.get(0).revenue());
        assertEquals(3, result.get(2).month());
        assertEquals(new BigDecimal("1250.50"), result.get(2).revenue());
        assertEquals(12, result.get(11).month());
        assertEquals(new BigDecimal("80.00"), result.get(11).revenue());
    }

    @Test
    void topBrandsMapsRowsAndPassesTheLimit() {
        when(vehicleRepository.topBrands(any(Pageable.class))).thenReturn(List.of(
                new Object[] {"Renault", 4L},
                new Object[] {"Peugeot", 2L}));

        var result = service.topBrands(5);

        assertEquals(2, result.size());
        assertEquals("Renault", result.get(0).brand());
        assertEquals(4, result.get(0).count());
        assertEquals("Peugeot", result.get(1).brand());
        verify(vehicleRepository).topBrands(org.springframework.data.domain.PageRequest.of(0, 5));
    }

    @Test
    void rejectsInvalidLimitAndYear() {
        assertThrows(BusinessException.class, () -> service.topBrands(0));
        assertThrows(BusinessException.class, () -> service.topBrands(21));
        assertThrows(BusinessException.class, () -> service.statusDistribution(1999));
        assertThrows(BusinessException.class, () -> service.revenueByMonth(2101));
        verify(vehicleRepository, never()).topBrands(any());
        verify(quoteRepository, never()).revenueByMonth(any(), any());
    }
}
