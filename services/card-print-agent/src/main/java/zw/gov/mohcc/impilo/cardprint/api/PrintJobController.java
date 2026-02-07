package zw.gov.mohcc.impilo.cardprint.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.cardprint.dto.CreatePrintJobRequest;
import zw.gov.mohcc.impilo.cardprint.dto.PrintJobResponse;
import zw.gov.mohcc.impilo.cardprint.dto.PrintJobSearchRequest;
import zw.gov.mohcc.impilo.cardprint.dto.UpdateJobStatusRequest;
import zw.gov.mohcc.impilo.cardprint.entity.PrintJobEntity;
import zw.gov.mohcc.impilo.cardprint.service.PrintJobService;
import zw.gov.mohcc.impilo.cardprint.service.spooler.SpoolerService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.io.IOException;
import java.util.UUID;

/**
 * REST controller for managing print job lifecycle.
 * All endpoints are internal (behind Envoy ext_authz) and require trust headers.
 */
@RestController
@RequestMapping("/v1/internal/print-jobs")
public class PrintJobController {

    private static final Logger log = LoggerFactory.getLogger(PrintJobController.class);

    private final PrintJobService printJobService;
    private final SpoolerService spoolerService;

    public PrintJobController(PrintJobService printJobService, SpoolerService spoolerService) {
        this.printJobService = printJobService;
        this.spoolerService = spoolerService;
    }

    /**
     * POST /v1/internal/print-jobs — Creates a new print job.
     */
    @PostMapping
    public ResponseEntity<PrintJobResponse> createJob(@Valid @RequestBody CreatePrintJobRequest request) {
        TrustContext ctx = TrustContextHolder.get();
        UUID tenantId = ctx.tenantId();
        String requestedBy = ctx.actorId() != null ? ctx.actorId() : "UNKNOWN";

        PrintJobResponse response = printJobService.createJob(request, tenantId, requestedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /v1/internal/print-jobs/{jobId} — Retrieves a single print job.
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<PrintJobResponse> getJob(@PathVariable UUID jobId) {
        PrintJobResponse response = printJobService.getJob(jobId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /v1/internal/print-jobs — Searches print jobs with optional filters.
     */
    @GetMapping
    public ResponseEntity<Page<PrintJobResponse>> searchJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        TrustContext ctx = TrustContextHolder.get();
        UUID tenantId = ctx.tenantId();

        PrintJobSearchRequest searchRequest = new PrintJobSearchRequest(status, subjectType, subjectId, page, size);
        Page<PrintJobResponse> results = printJobService.searchJobs(tenantId, searchRequest);

        return ResponseEntity.ok(results);
    }

    /**
     * POST /v1/internal/print-jobs/{jobId}/status — Updates job status.
     */
    @PostMapping("/{jobId}/status")
    public ResponseEntity<PrintJobResponse> updateJobStatus(
            @PathVariable UUID jobId,
            @Valid @RequestBody UpdateJobStatusRequest request) {

        TrustContext ctx = TrustContextHolder.get();
        String actorId = ctx.actorId() != null ? ctx.actorId() : "UNKNOWN";

        PrintJobResponse response = printJobService.updateJobStatus(jobId, request, actorId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /v1/internal/print-jobs/{jobId}/cancel — Cancels a print job.
     */
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<PrintJobResponse> cancelJob(@PathVariable UUID jobId) {
        TrustContext ctx = TrustContextHolder.get();
        String actorId = ctx.actorId() != null ? ctx.actorId() : "UNKNOWN";

        PrintJobResponse response = printJobService.cancelJob(jobId, actorId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /v1/internal/print-jobs/{jobId}/pdf — Downloads the rendered PDF for a job.
     */
    @GetMapping("/{jobId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID jobId) {
        PrintJobEntity entity = printJobService.getJobEntity(jobId);

        if (entity.getStatus() != PrintJobEntity.JobStatus.RENDERED
                && entity.getStatus() != PrintJobEntity.JobStatus.PRINTING
                && entity.getStatus() != PrintJobEntity.JobStatus.PRINTED
                && entity.getStatus() != PrintJobEntity.JobStatus.COLLECTED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Error", "PDF not yet rendered. Current status: " + entity.getStatus())
                    .build();
        }

        String fileName = buildFileName(entity);

        try {
            byte[] pdfBytes = spoolerService.retrieve(entity.getJobId(), fileName);
            if (pdfBytes == null) {
                return ResponseEntity.notFound().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentLength(pdfBytes.length);

            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (IOException e) {
            log.error("Failed to retrieve PDF for job {}: {}", jobId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String buildFileName(PrintJobEntity job) {
        return String.format("%s_%s_%s.pdf",
                job.getJobType().name().toLowerCase(),
                job.getSubjectId().replaceAll("[^a-zA-Z0-9-]", "_"),
                job.getJobId().toString().substring(0, 8));
    }
}
