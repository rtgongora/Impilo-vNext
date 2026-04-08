package zw.gov.mohcc.impilo.varapi.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.CertificateResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.CpdSummaryResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.PortalMeResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.UploadEvidenceRequest;
import zw.gov.mohcc.impilo.varapi.core.CpdService;
import zw.gov.mohcc.impilo.varapi.core.LicenseService;
import zw.gov.mohcc.impilo.varapi.core.ProviderService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;

import java.util.List;

/**
 * Portal REST API for authenticated providers to manage their own profile.
 *
 * This controller serves the provider self-service portal. It uses the
 * actor_id from TrustContext to identify the current provider, rather
 * than accepting a providerPublicId as a path variable.
 *
 * Endpoints cover profile viewing, CPD tracking, evidence upload,
 * and certificate downloads (with step-up authentication check).
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/portal")
public class PortalController {

    private static final Logger log = LoggerFactory.getLogger(PortalController.class);

    private final ProviderService providerService;
    private final CpdService cpdService;
    private final LicenseService licenseService;

    public PortalController(ProviderService providerService,
                            CpdService cpdService,
                            LicenseService licenseService) {
        this.providerService = providerService;
        this.cpdService = cpdService;
        this.licenseService = licenseService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PortalMeResponse>> getMyProfile() {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Portal: fetching own profile [actorId={}] correlationId={}",
                ctx.actorId(), ctx.correlationId());

        ProviderEntity provider = providerService.getProvider(ctx.actorId()).provider();
        PortalMeResponse response = toPortalMeResponse(provider);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/cpd")
    public ResponseEntity<ApiResponse<CpdSummaryResponse>> getCpdSummary() {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Portal: fetching CPD summary [actorId={}] correlationId={}",
                ctx.actorId(), ctx.correlationId());

        ProviderEntity provider = providerService.getProvider(ctx.actorId()).provider();
        CpdService.CpdSummary summary = cpdService.getCpdSummary(provider.getProviderPublicId());
        CpdSummaryResponse response = toCpdSummaryResponse(summary);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/cpd/evidence")
    public ResponseEntity<ApiResponse<GenericResponse>> uploadEvidence(
            @Valid @RequestBody UploadEvidenceRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Portal: uploading CPD evidence [actorId={}, eventType={}] correlationId={}",
                ctx.actorId(), request.eventType(), ctx.correlationId());

        ProviderEntity provider = providerService.getProvider(ctx.actorId()).provider();
        cpdService.uploadEvidence(request.eventId(), request.documentId(), request.evidenceType(), request.notes());
        GenericResponse response = new GenericResponse("ACCEPTED",
                "Evidence submitted for verification");

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/certificates")
    public ResponseEntity<ApiResponse<List<CertificateResponse>>> listCertificates() {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Portal: listing certificates [actorId={}] correlationId={}",
                ctx.actorId(), ctx.correlationId());

        ProviderEntity provider = providerService.getProvider(ctx.actorId()).provider();
        List<LicenseEntity> licenses = licenseService.getLicenseHistory(provider.getProviderPublicId());
        List<CertificateResponse> responses = licenses.stream()
                .map(this::toCertificateResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(responses, ctx.correlationId().toString()));
    }

    @GetMapping("/certificates/{id}/download")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable Long id) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Portal: downloading certificate [actorId={}, certificateId={}] correlationId={}",
                ctx.actorId(), id, ctx.correlationId());

        // Step-up check: verify the actor has elevated authentication for document download
        ProviderEntity provider = providerService.getProvider(ctx.actorId()).provider();
        byte[] pdf = licenseService.downloadCertificate(provider.getProviderPublicId(), id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"certificate-" + id + ".pdf\"")
                .body(pdf);
    }

    private CpdSummaryResponse toCpdSummaryResponse(CpdService.CpdSummary summary) {
        return new CpdSummaryResponse(
                summary.currentCycle() != null ? summary.currentCycle().getId() : null,
                summary.currentCycle() != null ? summary.currentCycle().getProvider().getProviderPublicId() : null,
                summary.currentCycle() != null ? summary.currentCycle().getCycleName() : null,
                summary.currentCycle() != null ? summary.currentCycle().getStartDate() : null,
                summary.currentCycle() != null ? summary.currentCycle().getEndDate() : null,
                summary.requiredPoints(),
                summary.earnedPoints(),
                summary.currentCycle() != null ? summary.currentCycle().getStatus() : "NONE",
                summary.requirementMet()
        );
    }

    // ---- Mapper: Entity → Portal Response DTOs ----

    private PortalMeResponse toPortalMeResponse(ProviderEntity entity) {
        return new PortalMeResponse(
                entity.getProviderPublicId(),
                entity.getTitle(),
                entity.getGivenName(),
                entity.getFamilyName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getProfession(),
                entity.getCadre(),
                entity.getPracticeNumber(),
                entity.getProfilePhotoRef(),
                entity.getStatus()
        );
    }

    private CertificateResponse toCertificateResponse(LicenseEntity entity) {
        return new CertificateResponse(
                entity.getId(),
                entity.getLicenseType(),
                entity.getLicenseNumber(),
                entity.getStatus(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.getCouncil() != null ? entity.getCouncil().getName() : null,
                entity.getIssuedAt()
        );
    }
}
