package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.trust.EmploymentMatchBffService;
import zw.gov.mohcc.impilo.experience.trust.TrustProfileComposer;

import java.util.Map;

/**
 * Trust profile + Channel B employment-match endpoints (IATG Wave-1, WS-D).
 *
 * <p>GET /api/v1/trust/profile/{healthId} composes the four doctrine blocks
 * (identity / professional / employment / operational), each citing its system
 * of record and degrading independently to {@code {"status":"UNAVAILABLE"}}.
 * The optional {@code ?providerId=} query param overrides provider resolution
 * for callers that already hold a provider identifier (numeric id or
 * providerPublicId).</p>
 *
 * <p>POST /api/v1/trust/employment-match runs the EC-number match and, when a
 * provider registration exists, asserts EMPLOYMENT_MATCHED trust. The subject
 * Health ID is server-authoritative — taken from the {@code X-Actor-ID} trust
 * header, never from the body — so a caller can only match employment for
 * themselves. EC numbers are always masked in responses.</p>
 */
@RestController
@RequestMapping("/api/v1/trust")
public class TrustProfileBffController {

    private final TrustProfileComposer trustProfileComposer;
    private final EmploymentMatchBffService employmentMatchBffService;

    public TrustProfileBffController(TrustProfileComposer trustProfileComposer,
                                     EmploymentMatchBffService employmentMatchBffService) {
        this.trustProfileComposer = trustProfileComposer;
        this.employmentMatchBffService = employmentMatchBffService;
    }

    @GetMapping("/profile/{healthId}")
    public ResponseEntity<Map<String, Object>> profile(
            @PathVariable String healthId,
            @RequestParam(name = "providerId", required = false) String providerId) {
        return ResponseEntity.ok(trustProfileComposer.compose(healthId, providerId));
    }

    @PostMapping("/employment-match")
    public ResponseEntity<Map<String, Object>> employmentMatch(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorHealthId,
            @RequestBody Map<String, Object> body) {
        String ecNumber = stringValue(body.get("ecNumber"));
        String providerId = stringValue(body.get("providerId"));
        Map<String, Object> result = employmentMatchBffService.matchAndAssert(actorHealthId, ecNumber, providerId);
        HttpStatus status = "INVALID_REQUEST".equals(result.get("status"))
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() || "null".equals(text) ? null : text;
    }
}
