package zw.gov.mohcc.impilo.rito.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.core.PublicCaseIntakeService;

import java.util.Map;
import java.util.UUID;

/**
 * Anonymous public intake lane (gateway-public-lane ADR, W4 {@code gateway-feedback-claim}).
 * Reached only through the experience-BFF public gateway controller, which enforces
 * fail-closed abuse controls (rate windows, body caps) before forwarding; Envoy's public
 * bypass strips trust headers, so only tenant/pod/request context arrives here — never an
 * actor. Responses disclose the case reference and claim-code-gated status only.
 */
@RestController
@RequestMapping("/v1/public/rito/cases")
public class PublicCaseIntakeController {

    private static final Logger log = LoggerFactory.getLogger(PublicCaseIntakeController.class);
    private static final int MAX_TITLE = 512;
    private static final int MAX_DESCRIPTION = 8000;
    private static final int MAX_CONTACT = 255;

    private final PublicCaseIntakeService intakeService;

    public PublicCaseIntakeController(PublicCaseIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    private static final int MAX_INCIDENT_REF = 128;

    public record PublicCaseRequest(String caseType, String feedbackCategory, String title,
                                    String description, UUID facilityId, String contact,
                                    String incidentRef) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> openCase(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody PublicCaseRequest body) {
        if (body.title() == null || body.title().isBlank()
                || body.description() == null || body.description().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title and description are required");
        }
        if (body.title().length() > MAX_TITLE || body.description().length() > MAX_DESCRIPTION
                || (body.contact() != null && body.contact().length() > MAX_CONTACT)
                || (body.incidentRef() != null && body.incidentRef().length() > MAX_INCIDENT_REF)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "field length exceeded");
        }
        try {
            PublicCaseIntakeService.PublicIntakeResult result = intakeService.openPublicCase(
                    tenantId, body.caseType(), body.feedbackCategory(), body.title().trim(),
                    body.description().trim(), body.facilityId(), body.contact(), body.incidentRef());
            log.info("Public case opened [reference={}] tenant={}", result.caseReference(), tenantId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "caseReference", result.caseReference(),
                    "claimCode", result.claimCode(),
                    "status", result.status()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Disclosure-limited status for the claim-code holder. Unknown codes 404 with the
     * same shape/timing regardless of why (no case enumeration oracle).
     */
    @GetMapping("/{claimCode}")
    public ResponseEntity<PublicCaseIntakeService.PublicCaseStatus> status(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable String claimCode) {
        return intakeService.statusByClaimCode(tenantId, claimCode)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown claim code"));
    }
}
