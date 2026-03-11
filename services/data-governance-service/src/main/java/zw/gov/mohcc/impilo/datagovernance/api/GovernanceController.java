package zw.gov.mohcc.impilo.datagovernance.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.datagovernance.api.dto.CreateDatasetRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.CreateGrantRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.DatasetResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.DecideRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.DecideResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.ExternalDatasetResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.GrantResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.PolicyResponse;
import zw.gov.mohcc.impilo.datagovernance.api.dto.PublishPolicyRequest;
import zw.gov.mohcc.impilo.datagovernance.api.dto.RevokeGrantRequest;
import zw.gov.mohcc.impilo.datagovernance.core.GovernanceService;
import zw.gov.mohcc.impilo.datagovernance.domain.DatasetEntity;
import zw.gov.mohcc.impilo.datagovernance.domain.GrantEntity;
import zw.gov.mohcc.impilo.datagovernance.domain.PolicyEntity;

import java.util.List;
import java.util.UUID;

@RestController
public class GovernanceController {

    private static final Logger log = LoggerFactory.getLogger(GovernanceController.class);
    private final GovernanceService governanceService;

    public GovernanceController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    // ── POST /internal/v1/governance/datasets — Register dataset ──

    @PostMapping("/internal/v1/governance/datasets")
    public ResponseEntity<?> createDataset(@Valid @RequestBody CreateDatasetRequest request,
                                            HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Create dataset [name={}] correlationId={}", request.name(), ctx.correlationId());

        DatasetEntity dataset = governanceService.createDataset(
                request,
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                idempotencyKey);

        DatasetResponse response = toDatasetResponse(dataset);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /internal/v1/governance/datasets — List datasets (internal) ──

    @GetMapping("/internal/v1/governance/datasets")
    public ResponseEntity<?> listDatasetsInternal() {
        RequestContext ctx = RequestContextHolder.require();
        log.info("List datasets correlationId={}", ctx.correlationId());

        List<DatasetResponse> datasets = governanceService
                .listDatasets(UUID.fromString(ctx.tenantId()))
                .stream()
                .map(this::toDatasetResponse)
                .toList();

        return ResponseEntity.ok(datasets);
    }

    // ── POST /internal/v1/governance/grants — Grant access ──

    @PostMapping("/internal/v1/governance/grants")
    public ResponseEntity<?> createGrant(@Valid @RequestBody CreateGrantRequest request,
                                          HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Create grant [principal={}, dataset={}, purpose={}] correlationId={}",
                request.principalId(), request.datasetId(), request.purposeOfUse(),
                ctx.correlationId());

        GrantEntity grant = governanceService.createGrant(
                request,
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                idempotencyKey);

        GrantResponse response = toGrantResponse(grant);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /internal/v1/governance/grants/revoke — Revoke an existing grant ──

    @PostMapping("/internal/v1/governance/grants/revoke")
    public ResponseEntity<?> revokeGrant(@Valid @RequestBody RevokeGrantRequest request,
                                          HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Revoke grant [grantId={}] correlationId={}", request.grantId(), ctx.correlationId());

        GrantEntity grant = governanceService.revokeGrant(
                request,
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                idempotencyKey);

        GrantResponse response = toGrantResponse(grant);
        return ResponseEntity.ok(response);
    }

    // ── POST /internal/v1/governance/policies — Publish a new policy version ──

    @PostMapping("/internal/v1/governance/policies")
    public ResponseEntity<?> publishPolicy(@Valid @RequestBody PublishPolicyRequest request,
                                            HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Publish policy [name={}] correlationId={}", request.name(), ctx.correlationId());

        PolicyEntity policy = governanceService.publishPolicy(
                request,
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                idempotencyKey);

        PolicyResponse response = toPolicyResponse(policy);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /internal/v1/governance/decide — Access decision ──

    @PostMapping("/internal/v1/governance/decide")
    public ResponseEntity<?> decide(@Valid @RequestBody DecideRequest request) {
        RequestContext ctx = RequestContextHolder.require();

        log.info("Decide [principal={}, dataset={}, purpose={}] correlationId={}",
                request.principalId(), request.dataset(), request.purposeOfUse(),
                ctx.correlationId());

        DecideResponse response = governanceService.decide(
                request,
                UUID.fromString(ctx.tenantId()),
                ctx.correlationId());

        return ResponseEntity.ok(response);
    }

    // ── GET /external/v1/governance/datasets — Policy-filtered listing ──

    @GetMapping("/external/v1/governance/datasets")
    public ResponseEntity<?> listDatasetsExternal() {
        RequestContext ctx = RequestContextHolder.require();
        log.info("External list datasets correlationId={}", ctx.correlationId());

        List<ExternalDatasetResponse> datasets = governanceService
                .listDatasets(UUID.fromString(ctx.tenantId()))
                .stream()
                .map(d -> new ExternalDatasetResponse(d.getDatasetId(), d.getName(), d.getClassification()))
                .toList();

        return ResponseEntity.ok(datasets);
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

    private GrantResponse toGrantResponse(GrantEntity entity) {
        return new GrantResponse(
                entity.getGrantId(),
                entity.getDatasetId(),
                entity.getPrincipalId(),
                entity.getPurposeOfUse(),
                entity.getExpiresAt() != null ? entity.getExpiresAt().toString() : null,
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
