package zw.gov.mohcc.impilo.dispatch.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.dispatch.api.dto.JobResponse;
import zw.gov.mohcc.impilo.dispatch.api.dto.JobSnapshotResponse;
import zw.gov.mohcc.impilo.dispatch.core.DispatchJobService;
import zw.gov.mohcc.impilo.dispatch.domain.DispatchJobEntity;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/internal/v1/snapshots")
public class SnapshotController {

    private static final Logger log = LoggerFactory.getLogger(SnapshotController.class);
    private final DispatchJobService jobService;

    public SnapshotController(DispatchJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/jobs")
    public ResponseEntity<JobSnapshotResponse> snapshotJobs(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(name = "as_of", required = false) String asOfParam) {

        var ctx = RequestContextHolder.require();
        log.info("Snapshot jobs [cursor={}, limit={}, as_of={}] correlationId={}",
                cursor, limit, asOfParam, ctx.correlationId());

        OffsetDateTime asOf = asOfParam != null
                ? OffsetDateTime.parse(asOfParam)
                : OffsetDateTime.now();

        // Tenant-scope the snapshot — without this it returned every tenant's jobs (a leak).
        java.util.UUID tenantId = java.util.UUID.fromString(ctx.tenantId());
        Page<DispatchJobEntity> page = jobService.getSnapshot(tenantId, asOf, cursor, limit);

        List<JobResponse> items = page.getContent().stream()
                .map(this::toJobResponse)
                .toList();

        String nextCursor = page.hasNext() ? String.valueOf(cursor + 1) : null;

        JobSnapshotResponse response = new JobSnapshotResponse(
                items, nextCursor, limit, page.hasNext(), asOf);

        return ResponseEntity.ok(response);
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
