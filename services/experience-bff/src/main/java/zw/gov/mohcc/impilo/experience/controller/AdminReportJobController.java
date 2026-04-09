package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.ReportJob;
import zw.gov.mohcc.impilo.experience.repository.ReportJobRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin report job listing for observability and export surfaces.
 *
 * <p>GET /internal/v1/admin/reports/jobs — paginated jobs for the tenant, newest first.</p>
 */
@RestController
@RequestMapping("/internal/v1/admin/reports/jobs")
public class AdminReportJobController {

    private final ReportJobRepository reportJobRepository;

    public AdminReportJobController(ReportJobRepository reportJobRepository) {
        this.reportJobRepository = reportJobRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listJobs(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "queuedAt"));

        Page<ReportJob> result = reportJobRepository.findByTenantIdOrderByQueuedAtDesc(tenantId, pageable);

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(ReportJob job) {
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
}

package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.ReportJob;
import zw.gov.mohcc.impilo.experience.repository.ReportJobRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin report job listing for observability and export surfaces.
 *
 * <p>GET /internal/v1/admin/reports/jobs — paginated jobs for the tenant, newest first.</p>
 */
@RestController
@RequestMapping("/internal/v1/admin/reports/jobs")
public class AdminReportJobController {

    private final ReportJobRepository reportJobRepository;

    public AdminReportJobController(ReportJobRepository reportJobRepository) {
        this.reportJobRepository = reportJobRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listJobs(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "queuedAt"));

        Page<ReportJob> result = reportJobRepository.findByTenantIdOrderByQueuedAtDesc(tenantId, pageable);

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::toResource)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toResource(ReportJob job) {
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
}
