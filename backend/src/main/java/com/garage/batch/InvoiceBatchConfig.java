package com.garage.batch;

import com.garage.domain.Invoice;
import com.garage.repository.InvoiceRepository;
import java.util.UUID;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.IteratorItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The invoice job: one chunk step (read appointment ids, process into invoices, write). The job is never launched
 * at startup (spring.batch.job.enabled=false); {@code InvoiceBatchService} launches it with a unique timestamp.
 */
@Configuration
public class InvoiceBatchConfig {

    public static final String JOB_NAME = "invoiceJob";
    public static final String STEP_NAME = "invoiceStep";
    /** Appointments per transaction. */
    static final int CHUNK_SIZE = 20;
    /** More skipped appointments than this in one run makes the run fail. */
    static final int SKIP_LIMIT = 1000;

    @Bean
    Job invoiceJob(JobRepository jobRepository, Step invoiceStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(invoiceStep).build();
    }

    @Bean
    Step invoiceStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                     ItemReader<UUID> pendingAppointmentReader, InvoiceProcessor invoiceProcessor,
                     ItemWriter<Invoice> invoiceWriter, InvoiceSkipListener invoiceSkipListener) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<UUID, Invoice>chunk(CHUNK_SIZE, transactionManager)
                .reader(pendingAppointmentReader)
                .processor(invoiceProcessor)
                .writer(invoiceWriter)
                .faultTolerant()
                .skipPolicy(new InvoiceSkipPolicy(SKIP_LIMIT))
                // A business skip does not roll the chunk back; any other error rolls it back and the chunk is
                // replayed item by item (the processor is not run again, so no invoice number is wasted).
                .noRollback(InvoiceSkipException.class)
                .processorNonTransactional()
                .listener(invoiceSkipListener)
                .build();
    }

    /** Snapshot of the ids to invoice, taken when the step starts. */
    @Bean
    @StepScope
    ItemReader<UUID> pendingAppointmentReader(InvoiceRepository invoiceRepository) {
        return new IteratorItemReader<>(invoiceRepository.findInvoiceableIds());
    }

    @Bean
    ItemWriter<Invoice> invoiceWriter(InvoiceRepository invoiceRepository) {
        // Flush now so a constraint violation (duplicate) is raised here, inside the writer, and can be skipped.
        return chunk -> invoiceRepository.saveAllAndFlush(chunk.getItems());
    }

    @Bean
    @StepScope
    InvoiceSkipListener invoiceSkipListener(BatchErrorRecorder recorder,
                                            @Value("#{stepExecution.jobExecutionId}") Long runId) {
        return new InvoiceSkipListener(recorder, runId);
    }
}
