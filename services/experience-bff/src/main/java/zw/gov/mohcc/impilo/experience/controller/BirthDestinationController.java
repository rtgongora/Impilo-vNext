package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.BirthDestinationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Birth-destination routing: can this facility be where she gives birth, for the level of care she
 * needs? Composition over tuso status + EmONC readiness; all the honesty rules live in
 * {@link BirthDestinationService}.
 */
@RestController
@RequestMapping("/internal/v1/maternity/birth-destination")
public class BirthDestinationController {

    private final BirthDestinationService service;

    public BirthDestinationController(BirthDestinationService service) {
        this.service = service;
    }

    /**
     * GET /internal/v1/maternity/birth-destination?facilityId=..&requiredLevel=CEMONC|BEMONC|UNKNOWN
     *
     * <p>Returns 502 when tuso could not be reached, so the caller cannot mistake an undetermined
     * result for either a safe or an unsafe destination.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> assess(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam long facilityId,
            @RequestParam(required = false, defaultValue = "UNKNOWN") String requiredLevel) {

        BirthDestinationService.RequiredLevel level = parseLevel(requiredLevel);
        BirthDestinationService.Verdict verdict = service.assess(facilityId, level);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", toMap(verdict));
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        // Undetermined is a gateway condition, not a 200 with a verdict — the whole point is that a
        // read failure must not be presented as a capability answer.
        if (verdict.status() == BirthDestinationService.DestinationStatus.UNAVAILABLE) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
        }
        return ResponseEntity.ok(body);
    }

    private static BirthDestinationService.RequiredLevel parseLevel(String raw) {
        try {
            return BirthDestinationService.RequiredLevel.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException e) {
            // An unrecognised level is UNKNOWN, not a guess — the service then reports the level as
            // not established rather than matching against an assumed one.
            return BirthDestinationService.RequiredLevel.UNKNOWN;
        }
    }

    private static Map<String, Object> toMap(BirthDestinationService.Verdict v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("facility_id", v.facilityId());
        m.put("required_level", v.requiredLevel().name());
        m.put("status", v.status().name());
        m.put("operational_gate_passed", v.operationalGatePassed());
        m.put("emonc_verdict", v.emoncVerdict());
        m.put("call_ahead", v.callAhead());
        m.put("message", v.message());
        m.put("guidance", v.guidance());
        return m;
    }
}
