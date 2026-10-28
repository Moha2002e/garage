package com.garage.service;

import com.garage.batch.InvoiceBatchConfig;
import com.garage.domain.Appointment;
import com.garage.domain.BatchError;
import com.garage.dto.InvoiceDtos.BatchErrorResponse;
import com.garage.dto.InvoiceDtos.BatchRunResponse;
import com.garage.dto.PageResponse;
import com.garage.exception.ConflictException;
import com.garage.repository.AppointmentRepository;
import com.garage.repository.BatchErrorRepository;
import com.garage.util.PageUtil;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs the invoice job and reads its history (runs and errors). ADMIN and EMPLOYEE, enforced by the controller. */
@Service
public class InvoiceBatchService {

    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Sort ERROR_SORT = Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by("id"));
    /** One run at a time in this instance: two parallel runs would only fight over the same appointments. */
    private static final ReentrantLock RUN_LOCK = new ReentrantLock();

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job invoiceJob;
    private final BatchErrorRepository batchErrorRepository;
    private final AppointmentRepository appointmentRepository;

    public InvoiceBatchService(JobLauncher jobLauncher, JobExplorer jobExplorer, Job invoiceJob,
                               BatchErrorRepository batchErrorRepository,
                               AppointmentRepository appointmentRepository) {
        this.jobLauncher = jobLauncher;
        this.jobExplorer = jobExplorer;
        this.invoiceJob = invoiceJob;
        this.batchErrorRepository = batchErrorRepository;
        this.appointmentRepository = appointmentRepository;
    }

    // ******************************************
    // ************ GET ************
    // ******************************************

    /** Runs, most recent first. Page size 10 or 25. */
    public PageResponse<BatchRunResponse> runs(int page, int size) {
        Pageable pageable = PageUtil.of(page, size, Sort.unsorted());
        long total = countRuns();
        List<BatchRunResponse> content = new ArrayList<>();
        if (total > 0) {
            List<JobInstance> instances = jobExplorer.getJobInstances(InvoiceBatchConfig.JOB_NAME,
                    (int) pageable.getOffset(), pageable.getPageSize());
            for (JobInstance instance : instances) {
                // Each run has its own unique parameter, so an instance has one execution.
                jobExplorer.getJobExecutions(instance).stream().findFirst().ifPresent(execution ->
                        content.add(toResponse(execution, batchErrorRepository.countByRunId(execution.getId()))));
            }
        }
        int totalPages = (int) Math.ceil(total / (double) pageable.getPageSize());
        return new PageResponse<>(content, pageable.getPageNumber(), pageable.getPageSize(), total, totalPages);
    }

    /** Errors, newest first, of one run when {@code runId} is given, else of all runs. Page size 10 or 25. */
    @Transactional(readOnly = true)
    public PageResponse<BatchErrorResponse> errors(Long runId, int page, int size) {
        Pageable pageable = PageUtil.of(page, size, ERROR_SORT);
        Page<BatchError> result = runId == null
                ? batchErrorRepository.findAll(pageable)
                : batchErrorRepository.findByRunId(runId, pageable);
        Map<UUID, Appointment> appointments = appointmentsOf(result.getContent());
        return PageResponse.from(result, e -> toResponse(e, appointments.get(e.getAppointmentId())));
    }

    // ******************************************
    // ************ POST ************
    // ******************************************

    /**
     * Launches the job and waits for its end (synchronous). The job parameter is the current timestamp, so every
     * call is a new job instance. A second call during a run answers 409. Rerunning is safe: an appointment that
     * already has an invoice is never selected again.
     */
    public BatchRunResponse run() {
        if (!RUN_LOCK.tryLock()) {
            throw new ConflictException("An invoice run is already in progress");
        }
        try {
            JobParameters parameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();
            JobExecution execution = jobLauncher.run(invoiceJob, parameters);
            return toResponse(execution, batchErrorRepository.countByRunId(execution.getId()));
        } catch (Exception e) {
            throw new IllegalStateException("The invoice job could not be launched", e);
        } finally {
            RUN_LOCK.unlock();
        }
    }

    // ******************************************
    // ************ UPDATE ************
    // ******************************************

    // Pas de PUT : l'historique des exécutions et des erreurs est en lecture seule.

    // ******************************************
    // ************ DELETE ************
    // ******************************************

    // Pas de DELETE : rien n'est supprimé.

    private long countRuns() {
        try {
            return jobExplorer.getJobInstanceCount(InvoiceBatchConfig.JOB_NAME);
        } catch (org.springframework.batch.core.launch.NoSuchJobException e) {
            // The job has never run: Spring Batch reports it as an unknown job.
            return 0;
        }
    }

    private Map<UUID, Appointment> appointmentsOf(List<BatchError> errors) {
        Set<UUID> ids = errors.stream().map(BatchError::getAppointmentId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Appointment> found = new HashMap<>();
        if (!ids.isEmpty()) {
            appointmentRepository.findWithDetailsByIdIn(ids).forEach(a -> found.put(a.getId(), a));
        }
        return found;
    }

    static BatchRunResponse toResponse(JobExecution execution, long errorCount) {
        long read = 0;
        long write = 0;
        long skip = 0;
        for (StepExecution step : execution.getStepExecutions()) {
            read += step.getReadCount();
            write += step.getWriteCount();
            skip += step.getSkipCount();
        }
        return new BatchRunResponse(execution.getId(), execution.getStatus().name(), read, write, skip, errorCount,
                toInstant(execution.getStartTime()), toInstant(execution.getEndTime()));
    }

    /** Spring Batch stores local date times in the zone of the JVM. */
    private static java.time.Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant();
    }

    static BatchErrorResponse toResponse(BatchError e, Appointment appointment) {
        String label = null;
        if (appointment != null) {
            label = LABEL_DATE.format(appointment.getDate().atZone(AppointmentService.PARIS)) + ", "
                    + InvoiceService.clientName(appointment.getClient()) + ", "
                    + InvoiceService.vehicleLabel(appointment.getVehicle());
        }
        return new BatchErrorResponse(e.getId(), e.getRunId(), e.getAppointmentId(), label, e.getCode(),
                e.getMessage(), e.getOccurredAt());
    }
}
