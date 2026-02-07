package zw.gov.mohcc.impilo.cardprint.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.cardprint.config.CardPrintProperties;
import zw.gov.mohcc.impilo.cardprint.entity.PrintAuditEntity;
import zw.gov.mohcc.impilo.cardprint.entity.PrintJobEntity;
import zw.gov.mohcc.impilo.cardprint.entity.PrintJobEntity.JobStatus;
import zw.gov.mohcc.impilo.cardprint.repository.PrintAuditRepository;
import zw.gov.mohcc.impilo.cardprint.repository.PrintJobRepository;
import zw.gov.mohcc.impilo.cardprint.service.PdfRenderService;
import zw.gov.mohcc.impilo.cardprint.service.spooler.SpoolerService;

import java.util.List;
import java.util.Map;

/**
 * Scheduled processor that picks up QUEUED print jobs, renders them into PDFs,
 * spools the output, and updates the job status.
 *
 * Runs on a configurable interval (default: every 5 seconds).
 */
@Component
public class PrintJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(PrintJobProcessor.class);

    private final PrintJobRepository printJobRepository;
    private final PrintAuditRepository printAuditRepository;
    private final PdfRenderService pdfRenderService;
    private final SpoolerService spoolerService;
    private final CardPrintProperties properties;

    public PrintJobProcessor(PrintJobRepository printJobRepository,
                             PrintAuditRepository printAuditRepository,
                             PdfRenderService pdfRenderService,
                             SpoolerService spoolerService,
                             CardPrintProperties properties) {
        this.printJobRepository = printJobRepository;
        this.printAuditRepository = printAuditRepository;
        this.pdfRenderService = pdfRenderService;
        this.spoolerService = spoolerService;
        this.properties = properties;
    }

    /**
     * Polls for QUEUED jobs and processes them in batch.
     * Each job is rendered, spooled, and marked as RENDERED.
     * Failures are caught per-job so one bad job does not block the batch.
     */
    @Scheduled(fixedDelayString = "${card-print.processor.poll-interval-ms:5000}")
    public void processQueuedJobs() {
        int batchSize = properties.getProcessor().getBatchSize();
        List<PrintJobEntity> jobs = printJobRepository
                .findByStatusOrderByPriorityDescCreatedAtAsc(JobStatus.QUEUED, PageRequest.of(0, batchSize));

        if (jobs.isEmpty()) {
            return;
        }

        log.info("Processing {} queued print jobs", jobs.size());

        for (PrintJobEntity job : jobs) {
            processJob(job);
        }
    }

    /**
     * Processes a single print job: transitions to RENDERING, renders PDF, spools output,
     * then transitions to RENDERED. On failure, marks the job as FAILED.
     */
    @Transactional
    public void processJob(PrintJobEntity job) {
        try {
            // Transition to RENDERING
            job.setStatus(JobStatus.RENDERING);
            job = printJobRepository.save(job);

            log.info("Rendering print job {} (type={}, subject={})",
                    job.getJobId(), job.getJobType(), job.getSubjectId());

            // Render PDF
            byte[] pdfBytes = pdfRenderService.render(job);

            // Spool output
            String fileName = buildFileName(job);
            spoolerService.spool(job.getJobId(), fileName, pdfBytes);

            // Transition to RENDERED
            job.setStatus(JobStatus.RENDERED);
            printJobRepository.save(job);

            // Audit
            printAuditRepository.save(new PrintAuditEntity(
                    job.getJobId(),
                    "RENDERED",
                    "SYSTEM",
                    Map.of("pdfSizeBytes", pdfBytes.length, "fileName", fileName)
            ));

            log.info("Successfully rendered print job {} ({} bytes)", job.getJobId(), pdfBytes.length);

        } catch (Exception e) {
            log.error("Failed to process print job {}: {}", job.getJobId(), e.getMessage(), e);

            job.setStatus(JobStatus.FAILED);
            job.setFailureReason(truncate(e.getMessage(), 1000));
            printJobRepository.save(job);

            printAuditRepository.save(new PrintAuditEntity(
                    job.getJobId(),
                    "RENDER_FAILED",
                    "SYSTEM",
                    Map.of("error", truncate(e.getMessage(), 500))
            ));
        }
    }

    private String buildFileName(PrintJobEntity job) {
        return String.format("%s_%s_%s.pdf",
                job.getJobType().name().toLowerCase(),
                job.getSubjectId().replaceAll("[^a-zA-Z0-9-]", "_"),
                job.getJobId().toString().substring(0, 8));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "Unknown error";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
