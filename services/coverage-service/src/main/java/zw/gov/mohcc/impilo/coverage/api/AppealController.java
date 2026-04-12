package zw.gov.mohcc.impilo.coverage.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.AppealResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.DecideAppealRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.ReviewAppealRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubmitAppealRequest;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.AppealEntity;
import zw.gov.mohcc.impilo.coverage.integration.MushexClaimResubmitClient;
import zw.gov.mohcc.impilo.coverage.domain.ClaimEntity;
import zw.gov.mohcc.impilo.coverage.repository.AppealRepository;
import zw.gov.mohcc.impilo.coverage.repository.ClaimRepository;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/appeals")
public class AppealController {

    private final AppealRepository appealRepository;
    private final ClaimRepository claimRepository;
    private final CoverageEventService eventService;
    private final MushexClaimResubmitClient mushexClaimResubmitClient;
    private final ObjectMapper objectMapper;

    public AppealController(AppealRepository appealRepository,
                            ClaimRepository claimRepository,
                            CoverageEventService eventService,
                            MushexClaimResubmitClient mushexClaimResubmitClient,
                            ObjectMapper objectMapper) {
        this.appealRepository = appealRepository;
        this.claimRepository = claimRepository;
        this.eventService = eventService;
        this.mushexClaimResubmitClient = mushexClaimResubmitClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<AppealResponse>> submit(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody SubmitAppealRequest request) throws JsonProcessingException {

        UUID tid = UUID.fromString(tenantId);
        UUID corr = CorrelationIds.fromHeader(correlationId);

        UUID coverageId = request.coverageId();
        if (coverageId == null) {
            coverageId = claimRepository.findById(request.claimId())
                    .filter(c -> c.getTenantId().equals(tid))
                    .map(ClaimEntity::getCoverageId)
                    .orElse(null);
        }

        String evidenceJson = request.evidence() == null || request.evidence().isEmpty()
                ? "{}"
                : objectMapper.writeValueAsString(request.evidence());

        AppealEntity appeal = new AppealEntity(
                tid,
                request.claimId().toString(),
                coverageId,
                request.appellantType(),
                request.appellantId(),
                request.reason(),
                evidenceJson);
        appealRepository.save(appeal);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appeal_id", appeal.getId().toString());
        payload.put("claim_id", appeal.getClaimId());
        payload.put("status", appeal.getStatus());
        payload.put("meta", CoverageEventService.meta(corr));
        eventService.emitAppealSubmitted(appeal.getId(), corr, tid, podId, appeal.getClaimId(), payload);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(toResponse(appeal)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AppealResponse>>> list(
            @RequestHeader("X-Tenant-ID") String tenantHeader,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID claimId,
            @RequestParam(required = false) String appellantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID effectiveTenant = tenantId != null ? tenantId : UUID.fromString(tenantHeader);
        Specification<AppealEntity> spec = (root, query, cb) -> {
            List<Predicate> parts = new ArrayList<>();
            parts.add(cb.equal(root.get("tenantId"), effectiveTenant));
            if (status != null && !status.isBlank()) {
                parts.add(cb.equal(root.get("status"), status));
            }
            if (claimId != null) {
                parts.add(cb.equal(root.get("claimId"), claimId.toString()));
            }
            if (appellantId != null && !appellantId.isBlank()) {
                parts.add(cb.equal(root.get("appellantId"), appellantId));
            }
            return cb.and(parts.toArray(Predicate[]::new));
        };

        Page<AppealEntity> result = appealRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<AppealResponse> mapped = result.map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.of(mapped));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppealResponse>> get(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID id) {
        UUID tid = UUID.fromString(tenantId);
        return appealRepository.findById(id)
                .filter(a -> a.getTenantId().equals(tid))
                .map(this::toResponse)
                .map(ApiResponse::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/review")
    @Transactional
    public ResponseEntity<ApiResponse<AppealResponse>> review(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id,
            @Valid @RequestBody ReviewAppealRequest body) {

        UUID tid = UUID.fromString(tenantId);
        AppealEntity appeal = appealRepository.findById(id).filter(a -> a.getTenantId().equals(tid)).orElse(null);
        if (appeal == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"PENDING".equals(appeal.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        appeal.markUnderReview(body.reviewerId());
        appealRepository.save(appeal);

        UUID corr = CorrelationIds.fromHeader(correlationId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appeal_id", appeal.getId().toString());
        payload.put("reviewer_id", body.reviewerId());
        payload.put("meta", CoverageEventService.meta(corr));
        eventService.emitAppealUnderReview(appeal.getId(), corr, tid, podId, appeal.getClaimId(), payload);

        return ResponseEntity.ok(ApiResponse.of(toResponse(appeal)));
    }

    @PutMapping("/{id}/decide")
    @Transactional
    public ResponseEntity<ApiResponse<AppealResponse>> decide(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id,
            @Valid @RequestBody DecideAppealRequest body) {

        String d = body.decision();
        if (!List.of("UPHELD", "OVERTURNED", "PARTIAL").contains(d)) {
            return ResponseEntity.badRequest().build();
        }

        UUID tid = UUID.fromString(tenantId);
        AppealEntity appeal = appealRepository.findById(id).filter(a -> a.getTenantId().equals(tid)).orElse(null);
        if (appeal == null) {
            return ResponseEntity.notFound().build();
        }
        if (!"UNDER_REVIEW".equals(appeal.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        appeal.decide(d, body.decisionReason(), body.decidedBy());
        appealRepository.save(appeal);

        UUID corr = CorrelationIds.fromHeader(correlationId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", appeal.getStatus());
        payload.put("meta", CoverageEventService.meta(corr));
        eventService.emitAppealDecided(
                appeal.getId(),
                corr,
                tid,
                podId,
                appeal.getClaimId(),
                d,
                body.decisionReason(),
                body.decidedBy(),
                appeal.getDecidedAt(),
                payload);

        if ("OVERTURNED".equals(d) || "PARTIAL".equals(d)) {
            mushexClaimResubmitClient.notifyClaimResubmitAfterAppeal(tid, podId, corr, appeal.getClaimId());
            Map<String, Object> resubmitPayload = new LinkedHashMap<>();
            resubmitPayload.put("meta", CoverageEventService.meta(corr));
            resubmitPayload.put("source", "appeal_overturned");
            resubmitPayload.put("appeal_id", appeal.getId().toString());
            eventService.emitClaimResubmitted(corr, tid, podId, appeal.getClaimId(), resubmitPayload);
        }

        return ResponseEntity.ok(ApiResponse.of(toResponse(appeal)));
    }

    private AppealResponse toResponse(AppealEntity a) {
        return new AppealResponse(
                a.getId(),
                a.getTenantId(),
                a.getClaimId(),
                a.getCoverageId(),
                a.getAppellantType(),
                a.getAppellantId(),
                a.getReason(),
                a.getEvidenceJson(),
                a.getStatus(),
                a.getDecision(),
                a.getDecisionReason(),
                a.getReviewerId(),
                a.getDecidedBy(),
                a.getDecidedAt(),
                a.getExpiresAt(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }

}
