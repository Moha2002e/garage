package com.garage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garage.domain.Appointment;
import com.garage.domain.BatchError;
import com.garage.domain.Client;
import com.garage.domain.InvoiceErrorCode;
import com.garage.domain.Vehicle;
import com.garage.dto.InvoiceDtos.BatchErrorResponse;
import com.garage.dto.InvoiceDtos.BatchRunResponse;
import com.garage.exception.ConflictException;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.BatchErrorRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceBatchServiceTest {

    @Mock JobLauncher jobLauncher;
    @Mock JobExplorer jobExplorer;
    @Mock BatchErrorRepository batchErrorRepository;
    @Mock AppointmentRepository appointmentRepository;

    Job job = mock(Job.class);
    InvoiceBatchService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceBatchService(jobLauncher, jobExplorer, job, batchErrorRepository, appointmentRepository);
    }

    private static JobExecution finishedExecution(long id) {
        JobExecution execution = new JobExecution(id);
        execution.setStatus(BatchStatus.COMPLETED);
        execution.setStartTime(LocalDateTime.of(2026, 10, 25, 10, 0, 0));
        execution.setEndTime(LocalDateTime.of(2026, 10, 25, 10, 0, 5));
        StepExecution step = execution.createStepExecution("invoiceStep");
        step.setReadCount(10);
        step.setWriteCount(8);
        step.setProcessSkipCount(1);
        step.setWriteSkipCount(1);
        return execution;
    }

    @Test
    void runResponseSumsTheStepCountsAndReadsTheErrorCountOfTheRun() {
        BatchRunResponse response = InvoiceBatchService.toResponse(finishedExecution(5L), 2);

        assertEquals(5L, response.id());
        assertEquals("COMPLETED", response.status());
        assertEquals(10, response.readCount());
        assertEquals(8, response.writeCount());
        assertEquals(2, response.skipCount());
        assertEquals(2, response.errorCount());
        assertEquals(LocalDateTime.of(2026, 10, 25, 10, 0, 0).atZone(ZoneId.systemDefault()).toInstant(),
                response.startTime());
        assertEquals(LocalDateTime.of(2026, 10, 25, 10, 0, 5).atZone(ZoneId.systemDefault()).toInstant(),
                response.endTime());
    }

    @Test
    void anUnfinishedRunHasNoEndTime() {
        JobExecution execution = new JobExecution(6L);
        execution.setStatus(BatchStatus.STARTED);
        assertNull(InvoiceBatchService.toResponse(execution, 0).endTime());
    }

    @Test
    void runLaunchesTheJobWithAUniqueTimestampParameter() throws Exception {
        when(jobLauncher.run(any(Job.class), any(JobParameters.class))).thenReturn(finishedExecution(9L));

        BatchRunResponse response = service.run();

        assertEquals(9L, response.id());
        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobLauncher).run(any(Job.class), parameters.capture());
        assertNotNull(parameters.getValue().getLong("timestamp"));
    }

    @Test
    void aLaunchFailureIsReportedAndDoesNotKeepTheRunLocked() throws Exception {
        when(jobLauncher.run(any(Job.class), any(JobParameters.class)))
                .thenThrow(new IllegalArgumentException("boom"))
                .thenReturn(finishedExecution(10L));

        assertThrows(IllegalStateException.class, () -> service.run());
        assertEquals(10L, service.run().id());
    }

    @Test
    void aSecondRunWhileOneIsInProgressIsAConflict() throws Exception {
        AtomicReference<Throwable> concurrent = new AtomicReference<>();
        when(jobLauncher.run(any(Job.class), any(JobParameters.class))).thenAnswer(invocation -> {
            // Another request (another thread) arrives while this run is executing.
            concurrent.set(CompletableFuture.supplyAsync(() -> {
                try {
                    service.run();
                    return (Throwable) null;
                } catch (RuntimeException e) {
                    return e;
                }
            }).get());
            return finishedExecution(11L);
        });

        assertEquals(11L, service.run().id());
        assertInstanceOf(ConflictException.class, concurrent.get());
    }

    @Test
    void errorLabelIsDateClientAndVehicleOrNull() {
        Client client = new Client();
        client.setFirstName("Jean");
        client.setLastName("Dupont");
        Vehicle vehicle = new Vehicle();
        vehicle.setBrand("Renault");
        vehicle.setModel("Clio");
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setClient(client);
        appointment.setVehicle(vehicle);
        appointment.setDate(Instant.parse("2026-10-20T12:30:00Z"));
        BatchError error = new BatchError();
        error.setId(UUID.randomUUID());
        error.setRunId(5L);
        error.setAppointmentId(appointment.getId());
        error.setCode(InvoiceErrorCode.EMPTY_QUOTE);
        error.setMessage("Accepted quote has no line");
        error.setOccurredAt(Instant.parse("2026-10-25T10:00:01Z"));

        BatchErrorResponse found = InvoiceBatchService.toResponse(error, appointment);
        assertEquals("20/10/2026 14:30, Jean Dupont, Renault Clio", found.appointmentLabel());
        assertEquals(InvoiceErrorCode.EMPTY_QUOTE, found.code());
        assertEquals(5L, found.runId());

        assertNull(InvoiceBatchService.toResponse(error, null).appointmentLabel());
    }
}
