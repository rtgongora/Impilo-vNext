package zw.gov.mohcc.impilo.dispatch.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.dispatch.api.dto.AssignJobRequest;
import zw.gov.mohcc.impilo.dispatch.api.dto.CreateJobRequest;
import zw.gov.mohcc.impilo.dispatch.api.dto.JobResponse;
import zw.gov.mohcc.impilo.dispatch.api.dto.UpdateStatusRequest;
import zw.gov.mohcc.impilo.dispatch.core.DispatchJobService;
import zw.gov.mohcc.impilo.dispatch.domain.DispatchJobEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class DispatchJobController {

    private static final Logger log = LoggerFactory.getLogger(DispatchJobController.class);
    private final DispatchJobService jobService;

    public DispatchJobController(DispatchJobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/internal/v1/dispatch/jobs")
    public ResponseEntity<?> createJob(
            @Valid @RequestBody CreateJobRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Create dispatch job [facilityRef={}] correlationId={}",
                request.facilityRef(), ctx.correlationId());

        DispatchJobEntity job = jobService.createJob(
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                idempotencyKey,
                request);

        return ResponseEntity.status(HttpStatus.CREATED).body(toJobResponse(job));
    }

    @PostMapping("/internal/v1/dispatch/jobs/{job_id}/assign")
    public ResponseEntity<?> assignJob(
            @PathVariable("job_id") UUID jobId,
            @Valid @RequestBody AssignJobRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Assign dispatch job [jobId={}, agent={}, vehicle={}] correlationId={}",
                jobId, request.agentRef(), request.vehicleRef(), ctx.correlationId());

        DispatchJobEntity job = jobService.assignJob(
                jobId,
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                idempotencyKey,
                request);

        return ResponseEntity.ok(toJobResponse(job));
    }

    @PostMapping("/internal/v1/dispatch/jobs/{job_id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable("job_id") UUID jobId,
            @Valid @RequestBody UpdateStatusRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Update dispatch job status [jobId={}, status={}] correlationId={}",
                jobId, request.status(), ctx.correlationId());

        DispatchJobEntity job = jobService.updateStatus(
                jobId,
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                idempotencyKey,
                request);

        return ResponseEntity.ok(toJobResponse(job));
    }

    @GetMapping("/internal/v1/dispatch/jobs/{job_id}")
    public ResponseEntity<?> getJob(@PathVariable("job_id") UUID jobId) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("Get dispatch job [jobId={}] correlationId={}", jobId, ctx.correlationId());

        Optional<DispatchJobEntity> job = jobService.getJob(jobId);
        if (job.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", "Dispatch job not found",
                            ctx.requestId(), ctx.correlationId()));
        }
        return ResponseEntity.ok(toJobResponse(job.get()));
    }

    @GetMapping("/internal/v1/dispatch/jobs")
    public ResponseEntity<?> listJobs(
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int limit) {

        RequestContext ctx = RequestContextHolder.require();
        log.info("List dispatch jobs [facility_id={}, status={}, cursor={}, limit={}] correlationId={}",
                facilityId, status, cursor, limit, ctx.correlationId());

        Page<DispatchJobEntity> page = jobService.listJobs(
                UUID.fromString(ctx.tenantId()), facilityId, status, cursor, limit);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toJobResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    private JobResponse toJobResponse(DispatchJobEntity entity) {
        return new JobResponse(
                entity.getJobId(),
                entity.getTenantId(),
                entity.getFacilityRef(),
                entity.getRequestRef(),
                entity.getStatus(),
                entity.getAssignedAgentRef(),
                entity.getAssignedVehicleRef(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
