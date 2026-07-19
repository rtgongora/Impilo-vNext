package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Add a child / dependant (Identity Journey Doctrine, CJ14). A guardian registers a minor as a
 * person in their own right — the child gets their <b>own</b> Health ID / CRID / CPID (the golden
 * VITO issuance path) — and a <b>time-bound guardian relationship</b> is recorded on mvumo (the
 * delegation act-of-record) so the guardian may act on the child's behalf until the age of
 * majority, after which the delegation expires and the young adult claims their own account
 * (the existing claim flow, CJ3). The guardian never becomes the child's identity — the child is
 * a distinct person from birth.
 *
 * <p>Both steps compose existing sovereign capabilities: VITO {@code /identity/register} and mvumo
 * {@code /delegations}. The guardian's identity is the authenticated actor (never client-supplied);
 * the relationship expiry is derived server-side from the child's date of birth.</p>
 */
@RestController
@RequestMapping("/internal/v1/citizen/dependants")
public class CitizenDependantController {

    private static final Logger log = LoggerFactory.getLogger(CitizenDependantController.class);

    /** Age of majority in Zimbabwe. Guardianship expires at the child's 18th birthday. */
    private static final int AGE_OF_MAJORITY = 18;

    private final VitoServiceClient vito;
    private final MvumoServiceClient mvumo;

    public CitizenDependantController(VitoServiceClient vito, MvumoServiceClient mvumo) {
        this.vito = vito;
        this.mvumo = mvumo;
    }

    public record AddDependantRequest(String givenName, String familyName, String dateOfBirth,
                                      String sex, List<String> scope) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> addDependant(
            @RequestBody AddDependantRequest req,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String guardianActorId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {

        if (guardianActorId == null || guardianActorId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "X-Actor-ID (the guardian) is required");
        }
        if (isBlank(req.givenName()) || isBlank(req.familyName()) || isBlank(req.dateOfBirth())) {
            return error(HttpStatus.BAD_REQUEST, "givenName, familyName and dateOfBirth are required");
        }
        LocalDate dob;
        try {
            dob = LocalDate.parse(req.dateOfBirth().trim());
        } catch (DateTimeParseException e) {
            return error(HttpStatus.BAD_REQUEST, "dateOfBirth must be an ISO date (YYYY-MM-DD)");
        }
        LocalDate majority = dob.plusYears(AGE_OF_MAJORITY);
        if (!majority.isAfter(LocalDate.now())) {
            // Already an adult — they register and hold their own account, not as a dependant.
            return error(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This person is at or over the age of majority and must hold their own account");
        }

        try {
            // 1. The child is a person in their own right — mint their own identity anchor.
            Map<String, Object> childReg = new LinkedHashMap<>();
            childReg.put("givenName", req.givenName());
            childReg.put("familyName", req.familyName());
            childReg.put("dateOfBirth", req.dateOfBirth());
            if (req.sex() != null) {
                childReg.put("sex", req.sex());
            }
            JsonNode registered = vito.registerIdentity(childReg);
            String childHealthId = text(registered, "healthId");
            if (childHealthId == null) {
                return error(HttpStatus.BAD_GATEWAY, "The dependant's identity could not be created");
            }

            // 2. Record the time-bound guardian relationship (expires at the 18th birthday).
            Map<String, Object> delegation = new LinkedHashMap<>();
            delegation.put("delegatorSubjectRef", childHealthId);   // the child (acted for)
            delegation.put("delegateActorId", guardianActorId);     // the guardian (acting)
            delegation.put("relationshipType", "GUARDIAN");
            delegation.put("legalBasis", "GUARDIANSHIP");
            delegation.put("scope", req.scope() != null && !req.scope().isEmpty() ? req.scope() : List.of("*"));
            delegation.put("expiresAt", majority.atStartOfDay().toInstant(ZoneOffset.UTC).toString());
            JsonNode relationship = mvumo.createDelegation(delegation);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "CREATED");
            // Returned to the AUTHORISED guardian so their app can act for the child (X-Subject-ID);
            // no Impilo ID exists yet (minted at card issuance), so the subject ref is the handle.
            out.put("dependantSubjectRef", childHealthId);
            out.put("dependant", Map.of(
                    "givenName", req.givenName(),
                    "familyName", req.familyName(),
                    "dateOfBirth", req.dateOfBirth()));
            out.put("guardianship", Map.of(
                    "type", "GUARDIAN",
                    "expiresAt", majority.toString(),
                    "relationship", relationship == null ? Map.of() : relationship));
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(out, requestId, correlationId));
        } catch (Exception e) {
            log.warn("add-dependant failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "The dependant could not be added. Please try again.");
        }
    }

    /** Wrap a success payload in the house {@code {data, meta}} envelope the shell expects. */
    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", meta);
        return body;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String text(JsonNode n, String field) {
        if (n == null) {
            return null;
        }
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", status.name(), "message", message)));
    }
}
