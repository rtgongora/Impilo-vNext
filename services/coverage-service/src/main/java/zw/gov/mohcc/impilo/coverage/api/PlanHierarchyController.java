package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.HierarchyDtos.CreatePlanVersionRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.HierarchyDtos.CreateProductRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.HierarchyDtos.CreateSchemeRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.HierarchyDtos.PlanVersionResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.HierarchyDtos.ProductResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.HierarchyDtos.SchemeResponse;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.PayerEntity;
import zw.gov.mohcc.impilo.coverage.domain.PlanVersionEntity;
import zw.gov.mohcc.impilo.coverage.domain.ProductEntity;
import zw.gov.mohcc.impilo.coverage.domain.SchemeEntity;
import zw.gov.mohcc.impilo.coverage.repository.PayerRepository;
import zw.gov.mohcc.impilo.coverage.repository.PlanVersionRepository;
import zw.gov.mohcc.impilo.coverage.repository.ProductRepository;
import zw.gov.mohcc.impilo.coverage.repository.SchemeRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scheme -> Product -> PlanVersion hierarchy (spec §9). Published plan versions
 * are immutable — corrections create a new version, never mutate a published one.
 */
@RestController
@RequestMapping("/internal/v1/coverage")
public class PlanHierarchyController {

    private static final Set<String> COVERAGE_TYPES = Set.of(
            "SELF_PAY", "MEDICAL_AID", "COMMERCIAL_INSURANCE", "EMPLOYER_SPONSORED",
            "GOVERNMENT_PROGRAMME", "SOCIAL_PROTECTION", "DONOR_PROGRAMME", "FACILITY_WAIVER",
            "OCCUPATIONAL_INJURY", "MOTOR_VEHICLE_ACCIDENT", "TRAVEL_INSURANCE", "CAPITATED_CARE",
            "CONTRACTED_PACKAGE", "EMERGENCY_PROVISIONAL", "OTHER");

    private final PayerRepository payerRepository;
    private final SchemeRepository schemeRepository;
    private final ProductRepository productRepository;
    private final PlanVersionRepository planVersionRepository;
    private final CoverageEventService eventService;

    public PlanHierarchyController(PayerRepository payerRepository, SchemeRepository schemeRepository,
                                   ProductRepository productRepository, PlanVersionRepository planVersionRepository,
                                   CoverageEventService eventService) {
        this.payerRepository = payerRepository;
        this.schemeRepository = schemeRepository;
        this.productRepository = productRepository;
        this.planVersionRepository = planVersionRepository;
        this.eventService = eventService;
    }

    // ---- Schemes -----------------------------------------------------------

    @GetMapping("/payers/{payerId}/schemes")
    public ResponseEntity<List<SchemeResponse>> listSchemes(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID payerId) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(schemeRepository.findByTenantIdAndPayerRef(tid, payerId)
                .stream().map(SchemeResponse::of).toList());
    }

    @PostMapping("/payers/{payerId}/schemes")
    @Transactional
    public ResponseEntity<SchemeResponse> createScheme(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @PathVariable UUID payerId,
            @Valid @RequestBody CreateSchemeRequest req) {
        UUID tid = UUID.fromString(tenantId);
        PayerEntity payer = payerRepository.findByIdAndTenantId(payerId, tid)
                .orElseThrow(() -> new IllegalArgumentException("Payer not found: " + payerId));
        String coverageType = req.coverageType().trim().toUpperCase(Locale.ROOT);
        if (!COVERAGE_TYPES.contains(coverageType)) {
            throw new IllegalArgumentException("Unknown coverage_type: " + req.coverageType());
        }
        SchemeEntity scheme = SchemeEntity.create(tid, podId, payer.getId(),
                req.schemeCode().trim(), req.name().trim(), coverageType);
        schemeRepository.save(scheme);
        return ResponseEntity.status(HttpStatus.CREATED).body(SchemeResponse.of(scheme));
    }

    // ---- Products ----------------------------------------------------------

    @GetMapping("/schemes/{schemeId}/products")
    public ResponseEntity<List<ProductResponse>> listProducts(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID schemeId) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(productRepository.findByTenantIdAndSchemeRef(tid, schemeId)
                .stream().map(ProductResponse::of).toList());
    }

    @PostMapping("/schemes/{schemeId}/products")
    @Transactional
    public ResponseEntity<ProductResponse> createProduct(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @PathVariable UUID schemeId,
            @Valid @RequestBody CreateProductRequest req) {
        UUID tid = UUID.fromString(tenantId);
        SchemeEntity scheme = schemeRepository.findByIdAndTenantId(schemeId, tid)
                .orElseThrow(() -> new IllegalArgumentException("Scheme not found: " + schemeId));
        ProductEntity product = ProductEntity.create(tid, podId, scheme.getId(),
                req.productCode().trim(), req.name().trim());
        productRepository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.of(product));
    }

    // ---- Plan versions -----------------------------------------------------

    @GetMapping("/products/{productId}/versions")
    public ResponseEntity<List<PlanVersionResponse>> listVersions(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID productId) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(planVersionRepository.findByTenantIdAndProductRef(tid, productId)
                .stream().map(PlanVersionResponse::of).toList());
    }

    /** Create the next DRAFT version for a product. */
    @PostMapping("/products/{productId}/versions")
    @Transactional
    public ResponseEntity<PlanVersionResponse> createVersion(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @PathVariable UUID productId,
            @Valid @RequestBody CreatePlanVersionRequest req) {
        UUID tid = UUID.fromString(tenantId);
        ProductEntity product = productRepository.findByIdAndTenantId(productId, tid)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        int next = (int) planVersionRepository.countByTenantIdAndProductRef(tid, product.getId()) + 1;
        PlanVersionEntity version = PlanVersionEntity.draft(tid, podId, product.getId(), next, req.currency());
        if (req.approvalAuthority() != null) version.setApprovalAuthority(req.approvalAuthority());
        if (req.claimsSubmissionDeadlineDays() != null) version.setClaimsSubmissionDeadlineDays(req.claimsSubmissionDeadlineDays());
        if (req.appealDeadlineDays() != null) version.setAppealDeadlineDays(req.appealDeadlineDays());
        planVersionRepository.save(version);
        return ResponseEntity.status(HttpStatus.CREATED).body(PlanVersionResponse.of(version));
    }

    /** Publish a DRAFT version — thereafter immutable. */
    @PostMapping("/plan-versions/{versionId}/publish")
    @Transactional
    public ResponseEntity<PlanVersionResponse> publish(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID versionId) {
        UUID tid = UUID.fromString(tenantId);
        PlanVersionEntity version = planVersionRepository.findByIdAndTenantId(versionId, tid)
                .orElseThrow(() -> new IllegalArgumentException("Plan version not found: " + versionId));
        if (version.isPublished()) {
            throw new IllegalArgumentException("Plan version already published (immutable): " + versionId);
        }
        if ("RETIRED".equals(version.getStatus())) {
            throw new IllegalArgumentException("Cannot publish a retired plan version: " + versionId);
        }
        version.setStatus("PUBLISHED");
        version.setApprovedAt(OffsetDateTime.now());
        version.setPublishedAt(OffsetDateTime.now());
        planVersionRepository.save(version);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plan_version_id", version.getId().toString());
        payload.put("product_ref", version.getProductRef().toString());
        payload.put("version_number", version.getVersionNumber());
        payload.put("currency", version.getCurrency());
        eventService.emitPlanVersionPublished(version.getId(), CorrelationIds.fromHeader(correlationId),
                tid, podId, payload);
        return ResponseEntity.ok(PlanVersionResponse.of(version));
    }
}
