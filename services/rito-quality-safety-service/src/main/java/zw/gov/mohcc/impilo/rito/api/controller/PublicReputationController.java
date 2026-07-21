package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.ExperienceSummaryResponse;
import zw.gov.mohcc.impilo.rito.core.ReputationReadService;

import java.util.UUID;

/**
 * Public verified-experience summary (RW5) — the read VARAPI/TUSO compose onto a provider
 * profile. Verified fields only, experience domains only, no respondent identity;
 * source-tagged "Rito". Reached via the gateway/BFF (header-stripped public lane).
 */
@RestController
@RequestMapping("/v1/public/rito/reputation")
public class PublicReputationController {

    private final ReputationReadService readService;

    public PublicReputationController(ReputationReadService readService) {
        this.readService = readService;
    }

    @GetMapping("/{providerPublicId}")
    public ResponseEntity<ExperienceSummaryResponse> experienceSummary(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable String providerPublicId,
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(readService.publicExperienceSummary(tenantId, providerPublicId, period));
    }
}
