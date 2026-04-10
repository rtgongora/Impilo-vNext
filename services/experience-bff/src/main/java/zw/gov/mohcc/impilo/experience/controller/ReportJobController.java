package zw.gov.mohcc.impilo.experience.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.ReportJob;
import zw.gov.mohcc.impilo.experience.repository.ReportJobRepository;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoint 3: Edge-function-like job trigger — POST /internal/v1/reports/generate
 * Enqueues a report generation job in DB and writes an outbox event.
 */
@RestController
@RequestMapping("/internal/v1/reports")
public class ReportJobController {

    private final ReportJobRepository reportJobRepository;
    private final OutboxService outboxService;

    public ReportJobController(ReportJobRepository reportJobRepository, OutboxService outboxService) {
        this.reportJobRepository = reportJobRepository;
        this.outboxService = outboxService;
    }

    public record GenerateReportRequest(
            @NotBlank String report_type,
            Map<String, Object> parameters,
            @NotBlank String requested_by
    ) {}

    /**
     * Tenant-scoped job detail — same resource shape as admin list rows.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getJob(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        final UUID jobId;
        try {
            jobId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return reportJobRepository.findById(jobId)
                .filter(job -> tenantId.equals(job.getTenantId()))
                .map(job -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("data", toJobResource(job));
                    response.put("meta", Map.of(
                            "request_id", requestId,
                            "correlation_id", correlationId
                    ));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toJobResource(ReportJob job) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("report_type", job.getReportType());
        attributes.put("status", job.getStatus());
        attributes.put("requested_by", job.getRequestedBy());
        attributes.put("parameters", job.getParameters());
        attributes.put("result_url", job.getResultUrl());
        attributes.put("error_message", job.getErrorMessage());
        attributes.put("queued_at", job.getQueuedAt());
        attributes.put("started_at", job.getStartedAt());
        attributes.put("completed_at", job.getCompletedAt());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", job.getId().toString());
        resource.put("type", "ReportJob");
        resource.put("attributes", attributes);
        return resource;
    }

    @PostMapping("/generate")
    @Transactional
    public ResponseEntity<Map<String, Object>> generateReport(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody GenerateReportRequest request) {

        String paramsJson;
        try {
            paramsJson = request.parameters() != null
                    ? new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request.parameters())
                    : "{}";
        } catch (Exception e) {
            paramsJson = "{}";
        }

        ReportJob job = new ReportJob(tenantId, request.report_type(), paramsJson, request.requested_by());
        job = reportJobRepository.save(job);

        outboxService.writeOutboxEvent(
                "impilo.experience.report.queued.v1",
                correlationId,
                requestId,
                idempotencyKey != null ? idempotencyKey : requestId,
                tenantId,
                podId,
                "ReportJob",
                job.getId().toString(),
                Map.of(
                        "report_job_id", job.getId().toString(),
                        "report_type", request.report_type(),
                        "requested_by", request.requested_by(),
                        "status", "QUEUED"
                ),
                Map.of()
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toJobResource(job));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
