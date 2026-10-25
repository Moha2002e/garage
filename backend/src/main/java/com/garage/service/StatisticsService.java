package com.garage.service;

import com.garage.domain.AppointmentStatus;
import com.garage.dto.AppointmentDtos.MonthStat;
import com.garage.dto.AppointmentDtos.StatisticsResponse;
import com.garage.exception.BusinessException;
import com.garage.repository.AppointmentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatisticsService {

    private final AppointmentRepository appointmentRepository;

    public StatisticsService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    /** 12 months for the given year, counted in Europe/Paris; months without appointments are 0. */
    @Transactional(readOnly = true)
    public StatisticsResponse monthly(int year) {
        if (year < 2000 || year > 2100) {
            throw new BusinessException("Year must be between 2000 and 2100");
        }
        Instant start = LocalDate.of(year, 1, 1).atStartOfDay(AppointmentService.PARIS).toInstant();
        Instant end = LocalDate.of(year + 1, 1, 1).atStartOfDay(AppointmentService.PARIS).toInstant();
        long[][] counts = new long[12][3];
        for (Object[] row : appointmentRepository.countByMonthAndStatus(start, end)) {
            int month = ((Number) row[0]).intValue();
            AppointmentStatus status = AppointmentStatus.valueOf((String) row[1]);
            counts[month - 1][status.ordinal()] = ((Number) row[2]).longValue();
        }
        List<MonthStat> months = IntStream.rangeClosed(1, 12)
                .mapToObj(m -> new MonthStat(m,
                        counts[m - 1][AppointmentStatus.PLANNED.ordinal()],
                        counts[m - 1][AppointmentStatus.COMPLETED.ordinal()],
                        counts[m - 1][AppointmentStatus.CANCELLED.ordinal()]))
                .toList();
        return new StatisticsResponse(year, months);
    }
}
