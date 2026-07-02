package zw.gov.mohcc.impilo.datagovernance.api;

import zw.gov.mohcc.impilo.datagovernance.core.TenantKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.datagovernance.api.dto.DatasetResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.PolicyResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.SnapshotResponse;
import zw.gov.mohcc.impilo.datagovernance.domain.DatasetEntity;
import zw.gov.mohcc.impilo.datagovernance.domain.PolicyEntity;
import zw.gov.mohcc.impilo.datagovernance.repository.DatasetRepository;
import zw.gov.mohcc.impilo.datagovernance.repository.PolicyRepository;

import java.util.List;
import java.util.UUID;

@RestController
public class SnapshotController {

    private static final Logger log = LoggerFactory.getLogger(SnapshotController.class);
    private static final int MAX_LIMIT = 200;

    private final DatasetRepository datasetRepository;
    private final PolicyRepository policyRepository;

    public SnapshotController(DatasetRepository datasetRepository,
                              PolicyRepository policyRepository) {
        this.datasetRepository = datasetRepository;
        this.policyRepository = policyRepository;
    }

    // ── GET /internal/v1/snapshots/datasets — Cursor-based dataset snapshot ──

    @GetMapping("/internal/v1/snapshots/datasets")
    public ResponseEntity<SnapshotResponse<DatasetResponse>> snapshotDatasets(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "50") int limit) {

        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = TenantKeys.tenantUuid(ctx.tenantId());
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        log.info("Snapshot datasets [cursor={}, limit={}] correlationId={}",
                cursor, effectiveLimit, ctx.correlationId());

        Pageable pageable = PageRequest.of(cursor, effectiveLimit, Sort.by("createdAt").ascending());
        Page<DatasetEntity> page = datasetRepository.findByTenantId(tenantId, pageable);

        List<DatasetResponse> items = page.getContent().stream()
                .map(this::toDatasetResponse)
                .toList();

        boolean hasNext = page.hasNext();
        String nextCursor = hasNext ? String.valueOf(cursor + 1) : null;

        return ResponseEntity.ok(new SnapshotResponse<>(items, nextCursor, effectiveLimit, hasNext));
    }

    // ── GET /internal/v1/snapshots/policies — Cursor-based policy snapshot ──

    @GetMapping("/internal/v1/snapshots/policies")
    public ResponseEntity<SnapshotResponse<PolicyResponse>> snapshotPolicies(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "50") int limit) {

        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = TenantKeys.tenantUuid(ctx.tenantId());
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        log.info("Snapshot policies [cursor={}, limit={}] correlationId={}",
                cursor, effectiveLimit, ctx.correlationId());

        Pageable pageable = PageRequest.of(cursor, effectiveLimit, Sort.by("createdAt").ascending());
        Page<PolicyEntity> page = policyRepository.findByTenantId(tenantId, pageable);

        List<PolicyResponse> items = page.getContent().stream()
                .map(this::toPolicyResponse)
                .toList();

        boolean hasNext = page.hasNext();
        String nextCursor = hasNext ? String.valueOf(cursor + 1) : null;

        return ResponseEntity.ok(new SnapshotResponse<>(items, nextCursor, effectiveLimit, hasNext));
    }

    private DatasetResponse toDatasetResponse(DatasetEntity entity) {
        return new DatasetResponse(
                entity.getDatasetId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getClassification(),
                entity.getDescription(),
                entity.getCreatedAt().toString());
    }

    private PolicyResponse toPolicyResponse(PolicyEntity entity) {
        return new PolicyResponse(
                entity.getPolicyId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getVersion(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getPublishedAt() != null ? entity.getPublishedAt().toString() : null,
                entity.getCreatedAt().toString());
    }
}
