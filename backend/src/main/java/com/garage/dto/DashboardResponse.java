package com.garage.dto;

import com.garage.domain.AppointmentStatus;
import com.garage.dto.AppointmentDtos.AppointmentResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Home page of ADMIN and EMPLOYEE: today's agenda (Europe/Paris day) and key figures.
 * The first seven fields are the original ones and never change; the others were added with the invoices.
 */
public record DashboardResponse(
        List<AppointmentResponse> today,
        long plannedNext7Days,
        long inProgress,
        long activeClients,
        long activeVehicles,
        long monthCompleted,
        BigDecimal monthRevenue,
        List<DashboardAppointment> todayAppointments,
        List<DashboardAppointment> inProgressNow,
        List<DashboardAppointment> upcoming,
        long unpaidInvoicesCount,
        BigDecimal unpaidInvoicesAmount,
        long pendingInvoiceCount) {

    /** Short form of an appointment: end = start + duration, clientName "First Last", vehicleLabel "Brand Model". */
    public record DashboardAppointment(UUID id, Instant start, Instant end, AppointmentStatus status,
                                       String clientName, String vehicleLabel) {
    }
}
