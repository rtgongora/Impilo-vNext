package zw.gov.mohcc.impilo.varapi.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.visibility.AggregateVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;
import zw.gov.mohcc.impilo.varapi.api.policy.ProviderRepresentation;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.CreateProviderRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderContactDto;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderIdentifierDto;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderSearchRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderSpecialtyDto;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderStandingSummary;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderSummary;
import zw.gov.mohcc.impilo.varapi.api.dto.StatusChangeRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.UpdateProviderRequest;
import zw.gov.mohcc.impilo.varapi.core.EligibilityService;
import zw.gov.mohcc.impilo.varapi.core.ProviderService;
import zw.gov.mohcc.impilo.varapi.integration.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;

import java.util.List;

/**
 * Internal REST API for provider management within the VARAPI Provider Registry.
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/internal/providers")
public class ProviderController {

    private static final Logger log = LoggerFactory.getLogger(ProviderController.class);

    private final ProviderService providerService;
    private final EligibilityService eligibilityService;
    private final WorkforceGovernanceClient workforceGovernanceClient;

    public ProviderController(ProviderService providerService,
                              EligibilityService eligibilityService,
                              WorkforceGovernanceClient workforceGovernanceClient) {
        this.providerService = providerService;
        this.eligibilityService = eligibilityService;
        this.workforceGovernanceClient = workforceGovernanceClient;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProviderResponse>> createProvider(
            @Valid @RequestBody CreateProviderRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating provider [givenName={}, familyName={}, profession={}] correlationId={}",
                request.givenName(), request.familyName(), request.profession(), ctx.correlationId());

        ProviderEntity entity = providerService.createProvider(request);
        ProviderResponse response = toProviderResponse(entity);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/{providerPublicId}")
    public ResponseEntity<ApiResponse<ProviderResponse>> getProvider(
            @PathVariable String providerPublicId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching provider [providerPublicId={}] correlationId={}",
                providerPublicId, ctx.correlationId());

        ProviderService.ProviderDetail detail = providerService.getProvider(providerPublicId);
        ProviderResponse response = toProviderDetailResponse(detail);

        VisibilityProfile vis = VisibilityContextHolder.current().orElse(null);
        if (AggregateVisibilityGuard.blocksRowLevelDetail(vis)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("VISIBILITY_AGGREGATE_ONLY",
                            "Full provider record is not available under aggregate-only visibility.",
                            HttpStatus.FORBIDDEN.value(),
                            ctx.correlationId().toString()));
        }
        response = ProviderRepresentation.apply(response, vis);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    /**
     * Lightweight provider standing summary for cross-service gating (commerce, booking, PIC).
     */
    @GetMapping("/{providerPublicId}/standing-summary")
    public ResponseEntity<ApiResponse<ProviderStandingSummary>> getStandingSummary(@PathVariable String providerPublicId) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderService.ProviderDetail detail = providerService.getProvider(providerPublicId);
        ProviderEntity p = detail.provider();

        // Derive “has valid license” from lifecycle/licence fields (full license model lives elsewhere in Varapi).
        boolean hasValidLicense = p.getLicenceStatus() != null
                && (p.getLicenceStatus().equalsIgnoreCase("ACTIVE") || p.getLicenceStatus().equalsIgnoreCase("VALID"));

        boolean picEligible = false;
        try {
            var eligibility = eligibilityService.checkPicEligibility(p.getId(), null);
            picEligible = eligibility != null && Boolean.TRUE.equals(eligibility.canServeAsPic());
        } catch (Exception ignored) {}

        ProviderStandingSummary summary = new ProviderStandingSummary(
                p.getProviderPublicId(),
                p.getStatus(),
                p.getLifecycleStatus(),
                p.getLicenceStatus(),
                p.isActive(),
                hasValidLicense,
                picEligible,
                p.getEffectiveTo()
        );
        return ResponseEntity.ok(ApiResponse.ok(summary, ctx.correlationId().toString()));
    }

    /**
     * Workforce Governance assignments for this provider (facilities, jurisdictions, orgs, etc.).
     * Empty array when governance integration is disabled or unreachable.
     */
    @GetMapping("/{providerPublicId}/workforce-assignments-summary")
    public ResponseEntity<ApiResponse<JsonNode>> workforceAssignmentsSummary(@PathVariable String providerPublicId) {
        TrustContext ctx = TrustContextHolder.require();
        JsonNode data = workforceGovernanceClient.fetchProviderAssignments(providerPublicId);
        if (data == null || data.isNull()) {
            return ResponseEntity.ok(ApiResponse.ok(JsonNodeFactory.instance.arrayNode(), ctx.correlationId().toString()));
        }
        return ResponseEntity.ok(ApiResponse.ok(data, ctx.correlationId().toString()));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<ProviderSummary>>> searchProviders(
            @Valid @RequestBody ProviderSearchRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Searching providers [query={}, profession={}, status={}] correlationId={}",
                request.query(), request.profession(), request.status(), ctx.correlationId());

        Page<ProviderEntity> entityPage = providerService.searchProviders(request,
                PageRequest.of(request.page(), request.size()));
        List<ProviderSummary> summaries = entityPage.getContent().stream()
                .map(this::toProviderSummary)
                .toList();
        PagedResponse<ProviderSummary> paged = PagedResponse.of(
                summaries, request.page(), request.size(), entityPage.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, ctx.correlationId().toString()));
    }

    @PutMapping("/{providerPublicId}")
    public ResponseEntity<ApiResponse<ProviderResponse>> updateProvider(
            @PathVariable String providerPublicId,
            @Valid @RequestBody UpdateProviderRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating provider [providerPublicId={}] correlationId={}",
                providerPublicId, ctx.correlationId());

        ProviderEntity entity = providerService.updateProvider(providerPublicId, request);
        ProviderResponse response = toProviderResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/{providerPublicId}/status")
    public ResponseEntity<ApiResponse<ProviderResponse>> changeStatus(
            @PathVariable String providerPublicId,
            @Valid @RequestBody StatusChangeRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Changing provider status [providerPublicId={}, newStatus={}] correlationId={}",
                providerPublicId, request.newStatus(), ctx.correlationId());

        ProviderEntity entity = providerService.changeStatus(providerPublicId, request);
        ProviderResponse response = toProviderResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    // ---- Mapper: Entity → Response DTO (basic, without related data) ----

    private ProviderResponse toProviderResponse(ProviderEntity entity) {
        return new ProviderResponse(
                entity.getProviderPublicId(),
                entity.getProviderRef(),
                entity.getImpiloHealthId(),
                entity.getTitle(),
                entity.getGivenName(),
                entity.getFamilyName(),
                entity.getDateOfBirth(),
                entity.getGender(),
                entity.getNationality(),
                entity.getNationalId(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getPracticeNumber(),
                entity.getProfession(),
                entity.getCadre(),
                entity.getPrimaryCouncil() != null ? entity.getPrimaryCouncil().getId() : null,
                entity.getEmploymentOrgId(),
                entity.getProfilePhotoRef(),
                entity.getStatus(),
                entity.getVersion(),
                null, // identifiers — not loaded for single-entity returns
                null, // specialties
                null, // contacts
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy()
        );
    }

    // ---- Mapper: ProviderDetail → Response DTO (full with identifiers, specialties, contacts) ----

    public ProviderResponse toProviderDetailResponse(ProviderService.ProviderDetail detail) {
        ProviderEntity entity = detail.provider();

        List<ProviderIdentifierDto> identifiers = detail.identifiers() != null
                ? detail.identifiers().stream()
                    .map(i -> new ProviderIdentifierDto(
                            i.getId(),
                            i.getIdentifierSystem(),
                            i.getIdentifierType(),
                            i.getIdentifierValue(),
                            i.getIssuingCouncilId(),
                            i.getStatus(),
                            i.getVerificationState(),
                            i.isPrimary(),
                            i.getEffectiveFrom(),
                            i.getEffectiveTo(),
                            i.getIssuedDate(),
                            i.getExpiryDate()))
                    .toList()
                : List.of();

        List<ProviderSpecialtyDto> specialties = detail.specialties() != null
                ? detail.specialties().stream()
                    .map(s -> new ProviderSpecialtyDto(
                            s.getId(), s.getSpecialtyCode(), s.getSpecialtyName(),
                            s.getSubspecialtyCode(), s.getSubspecialtyName(),
                            s.getZiboValidated() != null && s.getZiboValidated(),
                            s.getPrimarySpecialty() != null && s.getPrimarySpecialty(),
                            s.getEffectiveDate(), s.getEndDate()))
                    .toList()
                : List.of();

        List<ProviderContactDto> contacts = detail.contacts() != null
                ? detail.contacts().stream()
                    .map(c -> new ProviderContactDto(
                            c.getId(), c.getContactType(), c.getValue(),
                            c.getVerified() != null && c.getVerified(),
                            c.getPrimaryContact() != null && c.getPrimaryContact()))
                    .toList()
                : List.of();

        return new ProviderResponse(
                entity.getProviderPublicId(),
                entity.getProviderRef(),
                entity.getImpiloHealthId(),
                entity.getTitle(),
                entity.getGivenName(),
                entity.getFamilyName(),
                entity.getDateOfBirth(),
                entity.getGender(),
                entity.getNationality(),
                entity.getNationalId(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getPracticeNumber(),
                entity.getProfession(),
                entity.getCadre(),
                entity.getPrimaryCouncil() != null ? entity.getPrimaryCouncil().getId() : null,
                entity.getEmploymentOrgId(),
                entity.getProfilePhotoRef(),
                entity.getStatus(),
                entity.getVersion(),
                identifiers,
                specialties,
                contacts,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy()
        );
    }

    // ---- Mapper: Entity → Summary DTO ----

    private ProviderSummary toProviderSummary(ProviderEntity entity) {
        return new ProviderSummary(
                entity.getProviderPublicId(),
                entity.getImpiloHealthId(),
                entity.getTitle(),
                entity.getGivenName(),
                entity.getFamilyName(),
                entity.getProfession(),
                entity.getCadre(),
                entity.getStatus(),
                entity.getPracticeNumber()
        );
    }
}
