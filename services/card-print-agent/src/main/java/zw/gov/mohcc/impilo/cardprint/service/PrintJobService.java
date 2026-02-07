package zw.gov.mohcc.impilo.cardprint.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.cardprint.dto.CreatePrintJobRequest;
import zw.gov.mohcc.impilo.cardprint.dto.PrintJobResponse;
import zw.gov.mohcc.impilo.cardprint.dto.PrintJobSearchRequest;
import zw.gov.mohcc.impilo.cardprint.dto.UpdateJobStatusRequest;
import zw.gov.mohcc.impilo.cardprint.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.cardprint.entity.PrintAuditEntity;
import zw.gov.mohcc.impilo.cardprint.entity.PrintJobEntity;
import zw.gov.mohcc.impilo.cardprint.entity.PrintJobEntity.JobStatus;
import zw.gov.mohcc.impilo.cardprint.entity.PrintJobEntity.JobType;
import zw.gov.mohcc.impilo.cardprint.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.cardprint.repository.PrintAuditRepository;
import zw.gov.mohcc.impilo.cardprint.repository.PrintJobRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for managing print job lifecycle: creation, search, status transitions, and cancellation.
 */
@Service
public class PrintJobService {

    private static final Logger log = LoggerFactory.getLogger(PrintJobService.class);

    private final PrintJobRepository printJobRepository;
    private final PrintAuditRepository printAuditRepository;
    private final EventOutboxRepository eventOutboxRepository;

    public PrintJobService(PrintJobRepository printJobRepository,
                           PrintAuditRepository printAuditRepository,
                           EventOutboxRepository eventOutboxRepository) {
        this.printJobRepository = printJobRepository;
        this.printAuditRepository = printAuditRepository;
        this.eventOutboxRepository = eventOutboxRepository;
    }

    /**
     * Creates a new print job and writes a REQUESTED outbox event.
     */
    @Transactional
    public PrintJobResponse createJob(CreatePrintJobRequest request, UUID tenantId, String requestedBy) {
        PrintJobEntity entity = new PrintJobEntity();
        entity.setTenantId(tenantId);
        entity.setJobType(JobType.valueOf(request.jobType()));
        entity.setSubjectType(request.subjectType());
        entity.setSubjectId(request.subjectId());
        entity.setSubjectName(request.subjectName());
        entity.setTemplateName(request.templateName());
        entity.setPayload(request.payload());
        entity.setPriority(request.priority());
        entity.setRequestedBy(requestedBy);

        entity = printJobRepository.save(entity);

        // Audit
        printAuditRepository.save(new PrintAuditEntity(
                entity.getJobId(),
                "CREATED",
                requestedBy,
                Map.of("jobType", request.jobType(), "templateName", request.templateName())
        ));

        // Outbox event
        eventOutboxRepository.save(new EventOutboxEntity(
                "PrintJob",
                entity.getJobId().toString(),
                "cards.print.requested",
                Map.of(
                        "jobId", entity.getJobId().toString(),
                        "tenantId", tenantId.toString(),
                        "jobType", entity.getJobType().name(),
                        "subjectType", entity.getSubjectType(),
                        "subjectId", entity.getSubjectId(),
                        "requestedBy", requestedBy,
                        "priority", entity.getPriority(),
                        "createdAt", entity.getCreatedAt().toString()
                )
        ));

        log.info("Created print job {} for {} {} (type={})",
                entity.getJobId(), entity.getSubjectType(), entity.getSubjectId(), entity.getJobType());

        return PrintJobResponse.from(entity);
    }

    /**
     * Retrieves a print job by its public job ID.
     */
    @Transactional(readOnly = true)
    public PrintJobResponse getJob(UUID jobId) {
        PrintJobEntity entity = printJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Print job not found: " + jobId));
        return PrintJobResponse.from(entity);
    }

    /**
     * Retrieves the raw entity by job ID (for internal use by processor).
     */
    @Transactional(readOnly = true)
    public PrintJobEntity getJobEntity(UUID jobId) {
        return printJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Print job not found: " + jobId));
    }

    /**
     * Searches print jobs with optional filters and pagination.
     */
    @Transactional(readOnly = true)
    public Page<PrintJobResponse> searchJobs(UUID tenantId, PrintJobSearchRequest request) {
        JobStatus statusFilter = null;
        if (request.status() != null && !request.status().isBlank()) {
            statusFilter = JobStatus.valueOf(request.status());
        }

        PageRequest pageable = PageRequest.of(request.page(), request.size());

        Page<PrintJobEntity> page = printJobRepository.searchJobs(
                tenantId,
                statusFilter,
                request.subjectType(),
                request.subjectId(),
                pageable
        );

        return page.map(PrintJobResponse::from);
    }

    /**
     * Updates the status of a print job, with appropriate lifecycle side effects.
     */
    @Transactional
    public PrintJobResponse updateJobStatus(UUID jobId, UpdateJobStatusRequest request, String actorId) {
        PrintJobEntity entity = printJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Print job not found: " + jobId));

        JobStatus newStatus = JobStatus.valueOf(request.status());
        JobStatus oldStatus = entity.getStatus();

        validateStatusTransition(oldStatus, newStatus);

        entity.setStatus(newStatus);

        if (newStatus == JobStatus.FAILED && request.failureReason() != null) {
            entity.setFailureReason(request.failureReason());
        }

        if (newStatus == JobStatus.PRINTED) {
            entity.setPrintedAt(OffsetDateTime.now());
        }

        if (newStatus == JobStatus.COLLECTED) {
            entity.setCollectedAt(OffsetDateTime.now());
            if (request.collectedBy() != null) {
                entity.setCollectedBy(request.collectedBy());
            }
        }

        entity = printJobRepository.save(entity);

        // Audit
        printAuditRepository.save(new PrintAuditEntity(
                entity.getJobId(),
                "STATUS_CHANGED",
                actorId,
                Map.of("from", oldStatus.name(), "to", newStatus.name())
        ));

        // Outbox events for key lifecycle transitions
        if (newStatus == JobStatus.PRINTED) {
            eventOutboxRepository.save(new EventOutboxEntity(
                    "PrintJob",
                    entity.getJobId().toString(),
                    "cards.print.printed",
                    Map.of(
                            "jobId", entity.getJobId().toString(),
                            "tenantId", entity.getTenantId().toString(),
                            "subjectType", entity.getSubjectType(),
                            "subjectId", entity.getSubjectId(),
                            "printedAt", entity.getPrintedAt().toString()
                    )
            ));
        }

        if (newStatus == JobStatus.COLLECTED) {
            eventOutboxRepository.save(new EventOutboxEntity(
                    "PrintJob",
                    entity.getJobId().toString(),
                    "cards.print.collected",
                    Map.of(
                            "jobId", entity.getJobId().toString(),
                            "tenantId", entity.getTenantId().toString(),
                            "subjectType", entity.getSubjectType(),
                            "subjectId", entity.getSubjectId(),
                            "collectedAt", entity.getCollectedAt().toString(),
                            "collectedBy", entity.getCollectedBy() != null ? entity.getCollectedBy() : ""
                    )
            ));
        }

        log.info("Print job {} status changed: {} -> {}", jobId, oldStatus, newStatus);

        return PrintJobResponse.from(entity);
    }

    /**
     * Cancels a print job if it has not yet been printed.
     */
    @Transactional
    public PrintJobResponse cancelJob(UUID jobId, String actorId) {
        PrintJobEntity entity = printJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Print job not found: " + jobId));

        if (entity.getStatus() == JobStatus.PRINTED || entity.getStatus() == JobStatus.COLLECTED) {
            throw new IllegalStateException("Cannot cancel a job that has already been printed or collected");
        }

        if (entity.getStatus() == JobStatus.CANCELLED) {
            throw new IllegalStateException("Job is already cancelled");
        }

        JobStatus oldStatus = entity.getStatus();
        entity.setStatus(JobStatus.CANCELLED);
        entity = printJobRepository.save(entity);

        // Audit
        printAuditRepository.save(new PrintAuditEntity(
                entity.getJobId(),
                "CANCELLED",
                actorId,
                Map.of("previousStatus", oldStatus.name())
        ));

        log.info("Print job {} cancelled by {} (was {})", jobId, actorId, oldStatus);

        return PrintJobResponse.from(entity);
    }

    /**
     * Saves an updated entity (used by the processor after rendering).
     */
    @Transactional
    public PrintJobEntity saveEntity(PrintJobEntity entity) {
        return printJobRepository.save(entity);
    }

    /**
     * Validates that a status transition is allowed.
     */
    private void validateStatusTransition(JobStatus from, JobStatus to) {
        boolean valid = switch (from) {
            case QUEUED -> to == JobStatus.RENDERING || to == JobStatus.CANCELLED || to == JobStatus.FAILED;
            case RENDERING -> to == JobStatus.RENDERED || to == JobStatus.FAILED;
            case RENDERED -> to == JobStatus.PRINTING || to == JobStatus.CANCELLED || to == JobStatus.FAILED;
            case PRINTING -> to == JobStatus.PRINTED || to == JobStatus.FAILED;
            case PRINTED -> to == JobStatus.COLLECTED;
            case COLLECTED, FAILED, CANCELLED -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    String.format("Invalid status transition: %s -> %s", from, to));
        }
    }
}
