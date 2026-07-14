package zw.gov.mohcc.impilo.varapi.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderRecognitionResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.ProviderResponse;
import zw.gov.mohcc.impilo.varapi.core.AffiliationService;
import zw.gov.mohcc.impilo.varapi.core.ProviderRecognitionService;
import zw.gov.mohcc.impilo.varapi.core.ProviderService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAffiliationEntity;

import java.util.List;

/**
 * Experience-facing lookups keyed by the canonical Impilo / Health ID (UUID).
 * Provider public IDs and domain identifiers remain secondary linked surfaces.
 */
@RestController
@RequestMapping("/v1/internal/providers/by-health-id")
public class ProviderByHealthIdController {

    private static final Logger log = LoggerFactory.getLogger(ProviderByHealthIdController.class);

    private final ProviderService providerService;
    private final AffiliationService affiliationService;
    private final ProviderController providerControllerDelegate;
    private final ProviderRecognitionService recognitionService;

    public ProviderByHealthIdController(
            ProviderService providerService,
            AffiliationService affiliationService,
            ProviderController providerControllerDelegate,
            ProviderRecognitionService recognitionService) {
        this.providerService = providerService;
        this.affiliationService = affiliationService;
        this.providerControllerDelegate = providerControllerDelegate;
        this.recognitionService = recognitionService;
    }

    @GetMapping("/{healthId}")
    public ResponseEntity<ApiResponse<ProviderResponse>> getByHealthId(@PathVariable String healthId) {
        TrustContext ctx = TrustContextHolder.require();
        log.debug("Provider lookup by Impilo Health ID healthId={}", healthId);
        ProviderService.ProviderDetail detail = providerService.getProviderByImpiloHealthId(healthId);
        ProviderResponse body = providerControllerDelegate.toProviderDetailResponse(detail);
        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }

    @GetMapping("/{healthId}/affiliations")
    public ResponseEntity<ApiResponse<List<ProviderAffiliationEntity>>> affiliations(@PathVariable String healthId) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderService.ProviderDetail detail = providerService.getProviderByImpiloHealthId(healthId);
        List<ProviderAffiliationEntity> rows =
                affiliationService.getAffiliationsByProvider(detail.provider().getId());
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    /**
     * Recognition read-model for health-worker advantage surfaces (badge,
     * COSTA fee exemption, coverage subsidy opt-in). GENERIC RESPONSE
     * doctrine (see {@link EligibilityController}): unknown, malformed and
     * not-in-good-standing IDs all yield the identical
     * {@code recognised=false} body — never a 404, never extra detail.
     * Recognition is administrative/financial only; it MUST NOT drive
     * clinical-triage priority.
     */
    @GetMapping("/{healthId}/recognition")
    public ResponseEntity<ApiResponse<ProviderRecognitionResponse>> recognition(
            @PathVariable String healthId) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderRecognitionResponse body = recognitionService.recognitionByHealthId(healthId);
        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }

    @GetMapping("/{healthId}/notices")
    public ResponseEntity<ApiResponse<List<Object>>> notices(@PathVariable String healthId) {
        TrustContext ctx = TrustContextHolder.require();
        providerService.getProviderByImpiloHealthId(healthId);
        return ResponseEntity.ok(ApiResponse.ok(List.of(), ctx.correlationId().toString()));
    }
}
