package zw.gov.mohcc.impilo.datagovernance.deid.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.datagovernance.core.TenantKeys;
import zw.gov.mohcc.impilo.datagovernance.deid.api.dto.CreateDeidDatasetRequest;
import zw.gov.mohcc.impilo.datagovernance.deid.api.dto.DeidDatasetResponse;
import zw.gov.mohcc.impilo.datagovernance.deid.api.dto.DeidRunResponse;
import zw.gov.mohcc.impilo.datagovernance.deid.api.dto.ReleasePolicyResponse;
import zw.gov.mohcc.impilo.datagovernance.deid.api.dto.RequestDeidRunRequest;
import zw.gov.mohcc.impilo.datagovernance.deid.api.dto.UpsertReleasePolicyRequest;
import zw.gov.mohcc.impilo.datagovernance.deid.core.DeidRunService;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidDatasetEntity;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidReleaseManifestEntity;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidReleasePolicyEntity;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidRunEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * De-identification pipeline API (internal plane only — the raw rows this
 * controller accepts are CPID-keyed and must never transit the public edge).
 */
@RestController
public class DeidController {

    private static final Logger log = LoggerFactory.getLogger(DeidController.class);

    private final DeidRunService deidRunService;
    private final ObjectMapper objectMapper;

    public DeidController(DeidRunService deidRunService, ObjectMapper objectMapper) {
        this.deidRunService = deidRunService;
        this.objectMapper = objectMapper;
    }

    // ── POST /internal/v1/deid/datasets — Register a de-identification dataset ──

    @PostMapping("/internal/v1/deid/datasets")
    public ResponseEntity<?> createDataset(@Valid @RequestBody CreateDeidDatasetRequest request,
                                           HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Register deid dataset [code={}] correlationId={}",
                request.datasetCode(), ctx.correlationId());
        try {
            DeidDatasetEntity dataset = deidRunService.createDataset(
                    request,
                    TenantKeys.tenantUuid(ctx.tenantId()),
                    ctx.podId(),
                    ctx.correlationId(),
                    idempotencyKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(toDatasetResponse(dataset));
        } catch (IllegalArgumentException e) {
            return validationFailure(e);
        }
    }

    // ── GET /internal/v1/deid/datasets — List registered datasets ──

    @GetMapping("/internal/v1/deid/datasets")
    public ResponseEntity<?> listDatasets() {
        RequestContext ctx = RequestContextHolder.require();
        List<DeidDatasetResponse> datasets = deidRunService
                .listDatasets(TenantKeys.tenantUuid(ctx.tenantId()))
                .stream()
                .map(this::toDatasetResponse)
                .toList();
        return ResponseEntity.ok(datasets);
    }

    // ── PUT /internal/v1/deid/policies — Upsert a data-use release policy ──

    @PutMapping("/internal/v1/deid/policies")
    public ResponseEntity<?> upsertPolicy(@Valid @RequestBody UpsertReleasePolicyRequest request,
                                          HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Upsert deid release policy [code={}] correlationId={}",
                request.policyCode(), ctx.correlationId());
        try {
            DeidReleasePolicyEntity policy = deidRunService.upsertPolicy(
                    request,
                    TenantKeys.tenantUuid(ctx.tenantId()),
                    ctx.podId(),
                    ctx.correlationId(),
                    idempotencyKey);
            return ResponseEntity.ok(toPolicyResponse(policy));
        } catch (IllegalArgumentException e) {
            return validationFailure(e);
        }
    }

    // ── POST /internal/v1/deid/runs — Request a de-identification run ──

    @PostMapping("/internal/v1/deid/runs")
    public ResponseEntity<?> requestRun(@Valid @RequestBody RequestDeidRunRequest request,
                                        HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Request deid run [dataset={}, rows={}] correlationId={}",
                request.datasetCode(), request.sourceRows().size(), ctx.correlationId());
        try {
            DeidRunService.RunOutcome outcome = deidRunService.requestRun(
                    request,
                    TenantKeys.tenantUuid(ctx.tenantId()),
                    ctx.podId(),
                    ctx.correlationId(),
                    idempotencyKey);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toRunResponse(outcome.run(), request.datasetCode(),
                            outcome.manifestId(), outcome.releasedRows()));
        } catch (IllegalArgumentException e) {
            return validationFailure(e);
        }
    }

    // ── GET /internal/v1/deid/runs/{runId} — Run status + QA report ──

    @GetMapping("/internal/v1/deid/runs/{runId}")
    public ResponseEntity<?> getRun(@PathVariable("runId") UUID runId) {
        RequestContext ctx = RequestContextHolder.require();
        return deidRunService.getRun(runId, TenantKeys.tenantUuid(ctx.tenantId()))
                .<ResponseEntity<?>>map(run -> {
                    String datasetCode = deidRunService.getDataset(run.getDatasetId())
                            .map(DeidDatasetEntity::getDatasetCode)
                            .orElse(null);
                    UUID manifestId = deidRunService.getManifest(run.getRunId())
                            .map(DeidReleaseManifestEntity::getManifestId)
                            .orElse(null);
                    // Released rows are handed to the requester at run time only —
                    // this service persists manifests and stats, never row values.
                    return ResponseEntity.ok(toRunResponse(run, datasetCode, manifestId, null));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error_code", "RUN_NOT_FOUND",
                                "message", "No de-identification run " + runId + " for tenant")));
    }

    // ── Mappers ──

    private DeidDatasetResponse toDatasetResponse(DeidDatasetEntity entity) {
        return new DeidDatasetResponse(
                entity.getDatasetId(),
                entity.getDatasetCode(),
                entity.getName(),
                entity.getPurpose(),
                entity.getZoneTarget().name(),
                entity.getPolicyRef(),
                entity.getSecretRef(),
                entity.getStatus(),
                entity.getCreatedAt().toString());
    }

    private ReleasePolicyResponse toPolicyResponse(DeidReleasePolicyEntity entity) {
        return new ReleasePolicyResponse(
                entity.getPolicyId(),
                entity.getPolicyCode(),
                entity.getPurpose(),
                entity.getRequester(),
                entity.getRetentionDays(),
                entity.isReidProhibited(),
                entity.getKThreshold(),
                readTree(entity.getQuasiIdentifierRulesJson()),
                readTree(entity.getAllowlistColumnsJson()),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt().toString());
    }

    private DeidRunResponse toRunResponse(DeidRunEntity run, String datasetCode,
                                          UUID manifestId, List<Map<String, Object>> releasedRows) {
        return new DeidRunResponse(
                run.getRunId(),
                datasetCode,
                run.getStatus().name(),
                run.getRowsIn(),
                run.getRowsOut(),
                run.getRowsSuppressed(),
                run.getRejectionReason(),
                run.getPolicyVersion(),
                readTree(run.getQaReportJson()),
                manifestId,
                releasedRows,
                run.getRequestedAt().toString(),
                run.getCompletedAt() != null ? run.getCompletedAt().toString() : null);
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Stored JSON is unreadable", e);
        }
    }

    private ResponseEntity<Map<String, String>> validationFailure(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error_code", "VALIDATION_FAILED", "message", e.getMessage()));
    }
}
