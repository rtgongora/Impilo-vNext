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

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("report_type", job.getReportType());
        attributes.put("status", job.getStatus());
        attributes.put("requested_by", job.getRequestedBy());
        attributes.put("queued_at", job.getQueuedAt());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", job.getId().toString(),
                "type", "ReportJob",
                "attributes", attributes
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
