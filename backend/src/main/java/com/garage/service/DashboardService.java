package com.garage.service;

import com.garage.domain.Appointment;
import com.garage.domain.AppointmentStatus;
import com.garage.domain.InvoiceStatus;
import com.garage.domain.QuoteStatus;
import com.garage.dto.DashboardResponse;
import com.garage.dto.DashboardResponse.DashboardAppointment;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.ClientRepository;
import com.garage.repository.InvoiceRepository;
import com.garage.repository.QuoteRepository;
import com.garage.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final ZoneId PARIS = AppointmentService.PARIS;
    private static final int MAX_TODAY = 20;

    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final QuoteRepository quoteRepository;
    private final ClientRepository clientRepository;
    private final VehicleRepository vehicleRepository;
    private final InvoiceRepository invoiceRepository;

    public DashboardService(AppointmentRepository appointmentRepository, AppointmentService appointmentService,
                            QuoteRepository quoteRepository, ClientRepository clientRepository,
                            VehicleRepository vehicleRepository, InvoiceRepository invoiceRepository) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
        this.quoteRepository = quoteRepository;
        this.clientRepository = clientRepository;
        this.vehicleRepository = vehicleRepository;
        this.invoiceRepository = invoiceRepository;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    /** Everything is computed here: "today" and "this month" are calendar periods in Europe/Paris. */
    @Transactional(readOnly = true)
    public DashboardResponse get() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(PARIS);
        Instant dayStart = today.atStartOfDay(PARIS).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(PARIS).toInstant();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        Instant monthStart = firstOfMonth.atStartOfDay(PARIS).toInstant();
        Instant monthEnd = firstOfMonth.plusMonths(1).atStartOfDay(PARIS).toInstant();

        var todayAppointments = appointmentService.toResponses(
                appointmentRepository.findByStatusInAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                        List.of(AppointmentStatus.PLANNED, AppointmentStatus.IN_PROGRESS), dayStart, dayEnd));
        long plannedNext7Days = appointmentRepository.countByStatusAndDateGreaterThanEqualAndDateLessThan(
                AppointmentStatus.PLANNED, now, now.plus(7, ChronoUnit.DAYS));
        long monthCompleted = appointmentRepository.countByStatusAndDateGreaterThanEqualAndDateLessThan(
                AppointmentStatus.COMPLETED, monthStart, monthEnd);
        BigDecimal revenue = quoteRepository
                .sumTotals(QuoteStatus.ACCEPTED, AppointmentStatus.COMPLETED, monthStart, monthEnd);
        BigDecimal monthRevenue = (revenue == null ? BigDecimal.ZERO : revenue)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        // Short lists: today's appointments that are not cancelled (20 at most), the ones running now, the next 5.
        List<DashboardAppointment> todayShort = appointmentRepository
                .findByStatusInAndDateGreaterThanEqualAndDateLessThanOrderByDateAsc(
                        List.of(AppointmentStatus.PLANNED, AppointmentStatus.IN_PROGRESS,
                                AppointmentStatus.COMPLETED), dayStart, dayEnd)
                .stream().limit(MAX_TODAY).map(DashboardService::toShort).toList();
        List<DashboardAppointment> inProgressNow = appointmentRepository
                .findTop20ByStatusOrderByDateAsc(AppointmentStatus.IN_PROGRESS)
                .stream().map(DashboardService::toShort).toList();
        List<DashboardAppointment> upcoming = appointmentRepository
                .findTop5ByStatusAndDateGreaterThanEqualOrderByDateAsc(AppointmentStatus.PLANNED, now)
                .stream().map(DashboardService::toShort).toList();

        BigDecimal unpaid = invoiceRepository.sumTotalByStatus(InvoiceStatus.ISSUED);
        BigDecimal unpaidAmount = (unpaid == null ? BigDecimal.ZERO : unpaid)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        return new DashboardResponse(todayAppointments, plannedNext7Days,
                appointmentRepository.countByStatus(AppointmentStatus.IN_PROGRESS),
                clientRepository.countByActive(true), vehicleRepository.countByActive(true),
                monthCompleted, monthRevenue,
                todayShort, inProgressNow, upcoming,
                invoiceRepository.countByStatus(InvoiceStatus.ISSUED), unpaidAmount,
                invoiceRepository.countInvoiceable());
    }

    private static DashboardAppointment toShort(Appointment a) {
        return new DashboardAppointment(a.getId(), a.getDate(), a.getEndDate(), a.getStatus(),
                InvoiceService.clientName(a.getClient()), InvoiceService.vehicleLabel(a.getVehicle()));
    }
}
