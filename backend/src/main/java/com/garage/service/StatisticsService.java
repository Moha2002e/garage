package com.garage.service;

import com.garage.domain.AppointmentStatus;
import com.garage.dto.AppointmentDtos.BrandCount;
import com.garage.dto.AppointmentDtos.MonthRevenue;
import com.garage.dto.AppointmentDtos.MonthStat;
import com.garage.dto.AppointmentDtos.StatisticsResponse;
import com.garage.dto.AppointmentDtos.StatusCount;
import com.garage.exception.BusinessException;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.QuoteRepository;
import com.garage.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {

    private final AppointmentRepository appointmentRepository;
    private final QuoteRepository quoteRepository;
    private final VehicleRepository vehicleRepository;

    public StatisticsService(AppointmentRepository appointmentRepository, QuoteRepository quoteRepository,
                             VehicleRepository vehicleRepository) {
        this.appointmentRepository = appointmentRepository;
        this.quoteRepository = quoteRepository;
        this.vehicleRepository = vehicleRepository;
    }

    /** 12 months for the given year, counted in Europe/Paris; months without appointments are 0. */
    @Transactional(readOnly = true)
    public StatisticsResponse monthly(int year) {
        if (year < 2000 || year > 2100) {
            throw new BusinessException("Year must be between 2000 and 2100");
        }
        Instant start = LocalDate.of(year, 1, 1).atStartOfDay(AppointmentService.PARIS).toInstant();
        Instant end = LocalDate.of(year + 1, 1, 1).atStartOfDay(AppointmentService.PARIS).toInstant();
        long[][] counts = new long[12][AppointmentStatus.values().length];
        for (Object[] row : appointmentRepository.countByMonthAndStatus(start, end)) {
            int month = ((Number) row[0]).intValue();
            AppointmentStatus status = AppointmentStatus.valueOf((String) row[1]);
            counts[month - 1][status.ordinal()] = ((Number) row[2]).longValue();
        }
        List<MonthStat> months = IntStream.rangeClosed(1, 12)
                .mapToObj(m -> new MonthStat(m,
                        counts[m - 1][AppointmentStatus.PLANNED.ordinal()],
                        counts[m - 1][AppointmentStatus.IN_PROGRESS.ordinal()],
                        counts[m - 1][AppointmentStatus.COMPLETED.ordinal()],
                        counts[m - 1][AppointmentStatus.CANCELLED.ordinal()]))
                .toList();
        return new StatisticsResponse(year, months);
    }

    /** Appointments of the year per status (Europe/Paris); the four statuses are always returned. */
    @Transactional(readOnly = true)
    public List<StatusCount> statusDistribution(int year) {
        checkYear(year);
        long[] counts = new long[AppointmentStatus.values().length];
        for (Object[] row : appointmentRepository.countByMonthAndStatus(startOf(year), startOf(year + 1))) {
            counts[AppointmentStatus.valueOf((String) row[1]).ordinal()] += ((Number) row[2]).longValue();
        }
        return java.util.Arrays.stream(AppointmentStatus.values())
                .map(st -> new StatusCount(st, counts[st.ordinal()]))
                .toList();
    }

    /**
     * Revenue per month of the year (Europe/Paris, month of the appointment), 12 months always returned.
     * What counts as revenue: the total of the quotes with status ACCEPTED (the only QuoteStatus that is a
     * commitment of the customer; DRAFT is not agreed yet and REFUSED is lost) attached to appointments
     * COMPLETED (work actually done). This is the same rule as the dashboard monthRevenue.
     */
    @Transactional(readOnly = true)
    public List<MonthRevenue> revenueByMonth(int year) {
        checkYear(year);
        BigDecimal[] sums = new BigDecimal[12];
        for (Object[] row : quoteRepository.revenueByMonth(startOf(year), startOf(year + 1))) {
            sums[((Number) row[0]).intValue() - 1] = (BigDecimal) row[1];
        }
        return IntStream.rangeClosed(1, 12)
                .mapToObj(m -> new MonthRevenue(m, sums[m - 1] == null
                        ? new BigDecimal("0.00") : sums[m - 1].setScale(2, java.math.RoundingMode.HALF_UP)))
                .toList();
    }

    /** The most frequent brands among the active vehicles; limit between 1 and 20. */
    @Transactional(readOnly = true)
    public List<BrandCount> topBrands(int limit) {
        if (limit < 1 || limit > 20) {
            throw new BusinessException("Limit must be between 1 and 20");
        }
        return vehicleRepository.topBrands(PageRequest.of(0, limit)).stream()
                .map(row -> new BrandCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    private static void checkYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new BusinessException("Year must be between 2000 and 2100");
        }
    }

    private static Instant startOf(int year) {
        return LocalDate.of(year, 1, 1).atStartOfDay(AppointmentService.PARIS).toInstant();
    }
}
