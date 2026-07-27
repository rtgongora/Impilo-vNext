package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Regulator user administration (RB-6): the appointment roster of one regulator, and the acts that
 * change it — invite a colleague into a governed role, verify a pending appointment, end one.
 *
 * <p>This de-stubs the surface behind {@code /work/regulators/[id]/users}. The reads are the
 * org-registry appointment roster; the mutations proxy to org-registry's appointment lifecycle
 * (verify/end) and to the RB-4 hashed, single-use invitation rail.</p>
 *
 * <p><b>The mutations are LOA3-gated, fail-closed.</b> Administering who holds authority at a
 * statutory regulator is the same class of act as founding one (RB-3b): the administrator doing it
 * must themselves be authoritatively identity-proven, and an identity-assurance outage refuses
 * rather than opens the door. Whether the caller is actually an administrator of THIS regulator is
 * the PDP's job, enforced through the org-scoped work-context session and the RB-2 role split — the
 * BFF gate is the person-assurance half, not the authority half.</p>
 *
 * <p>The roster read is ungated: seeing who administers a regulator is not itself a privileged act,
 * and the org-scoped session already bounds which regulator a caller can reach.</p>
 */
@RestController
@RequestMapping("/api/v1/regulatory")
public class RegulatorUserAdminController {

    private static final Logger log = LoggerFactory.getLogger(RegulatorUserAdminController.class);

    /** Administering a regulator's people demands an authoritatively-verified identity. */
    private static final int MIN_ADMIN_ASSURANCE_RANK = 3; // LOA3

    private final OrganizationRegistryServiceClient orgRegistryClient;
    private final IdentityAssuranceServiceClient assuranceClient;

    public RegulatorUserAdminController(OrganizationRegistryServiceClient orgRegistryClient,
                                        IdentityAssuranceServiceClient assuranceClient) {
        this.orgRegistryClient = orgRegistryClient;
        this.assuranceClient = assuranceClient;
    }

    /** The appointment roster for one regulator — who administers it, in what role, at what status. */
    @GetMapping("/organizations/{organizationId}/appointments")
    public ResponseEntity<Map<String, Object>> roster(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String organizationId) {
        try {
            JsonNode data = orgRegistryClient.listAppointmentsForOrganization(organizationId);
            return ok(data, HttpStatus.OK, requestId, correlationId);
        } catch (Exception e) {
            log.warn("regulator roster unavailable org={}: {}", organizationId, e.getMessage());
            return upstream(requestId, correlationId, "The regulator roster could not be loaded.");
        }
    }

    /** The governed appointment-role vocabulary, so the invite form offers only real roles. */
    @GetMapping("/appointment-roles")
    public ResponseEntity<Map<String, Object>> roles(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(orgRegistryClient.listAppointmentRoles(), HttpStatus.OK, requestId, correlationId);
        } catch (Exception e) {
            return upstream(requestId, correlationId, "The appointment roles could not be loaded.");
        }
    }

    /** Invite a colleague into a governed role via the RB-4 hashed single-use invitation rail. */
    @PostMapping("/organizations/{organizationId}/invitations")
    public ResponseEntity<Map<String, Object>> invite(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @PathVariable String organizationId,
            @RequestBody Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> denied = assuranceGate(requestId, correlationId,
                "Confirm your personal identity to an authoritative level before inviting a colleague "
                        + "into a regulator role.");
        if (denied != null) {
            return denied;
        }
        // The inviter here is a regulatory appointment-holder administrator, not an authorized
        // representative — carry their Health ID (X-Actor-ID) as the inviter identity (RB-6/V011),
        // never as a rep UUID (which would not even bind to the UUID field).
        Map<String, Object> proxied = new LinkedHashMap<>(body);
        proxied.putIfAbsent("invitedByHealthId", actorId);
        return relay(() -> orgRegistryClient.createInvitation(organizationId, proxied),
                HttpStatus.CREATED, requestId, correlationId, "The invitation could not be issued.");
    }

    @PostMapping("/appointments/{appointmentId}/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String appointmentId,
            @RequestBody(required = false) Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> denied = assuranceGate(requestId, correlationId,
                "Confirm your personal identity to an authoritative level before verifying an appointment.");
        if (denied != null) {
            return denied;
        }
        return relay(() -> orgRegistryClient.verifyAppointment(appointmentId, body),
                HttpStatus.OK, requestId, correlationId, "The appointment could not be verified.");
    }

    @PostMapping("/appointments/{appointmentId}/end")
    public ResponseEntity<Map<String, Object>> end(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String appointmentId,
            @RequestBody(required = false) Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> denied = assuranceGate(requestId, correlationId,
                "Confirm your personal identity to an authoritative level before ending an appointment.");
        if (denied != null) {
            return denied;
        }
        // The org-registry succession guard (RB-2) refuses ending the last administrator; a 409 from
        // there is surfaced to the operator so they appoint a replacement first.
        return relay(() -> orgRegistryClient.endAppointment(appointmentId, body),
                HttpStatus.OK, requestId, correlationId, "The appointment could not be ended.");
    }

    // ── gate + plumbing ─────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> assuranceGate(String requestId, String correlationId,
                                                              String message) {
        if (claimantAssuranceRank() < MIN_ADMIN_ASSURANCE_RANK) {
            return error(HttpStatus.FORBIDDEN, "IDENTITY_PROOFING_REQUIRED", message, requestId, correlationId);
        }
        return null;
    }

    private int claimantAssuranceRank() {
        try {
            JsonNode status = assuranceClient.getAssuranceStatus();
            String level = status == null ? null : status.path("currentLevel").asText(null);
            return switch (level == null ? "LOA1" : level.trim().toUpperCase(Locale.ROOT)) {
                case "LOA4" -> 4;
                case "LOA3" -> 3;
                case "LOA2" -> 2;
                default -> 1;
            };
        } catch (Exception e) {
            log.warn("assurance status unavailable — treating as LOA1 (fail-closed): {}", e.getMessage());
            return 1;
        }
    }

    private interface Upstream {
        JsonNode call();
    }

    private ResponseEntity<Map<String, Object>> relay(Upstream upstream, HttpStatus okStatus,
                                                      String requestId, String correlationId, String failMessage) {
        try {
            JsonNode data = upstream.call();
            return ok(data == null ? null : data.path("data").isMissingNode() ? data : data.path("data"),
                    okStatus, requestId, correlationId);
        } catch (Exception e) {
            log.warn("regulator user-admin upstream failed: {}", e.getMessage());
            return upstream(requestId, correlationId, failMessage);
        }
    }

    private ResponseEntity<Map<String, Object>> ok(JsonNode data, HttpStatus okStatus,
                                                   String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data == null ? java.util.List.of() : data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(okStatus).body(response);
    }

    private ResponseEntity<Map<String, Object>> upstream(String requestId, String correlationId, String message) {
        return error(HttpStatus.BAD_GATEWAY, "ORG_REGISTRY_UNAVAILABLE", message, requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus statusCode, String code, String message,
                                                      String requestId, String correlationId) {
        return ResponseEntity.status(statusCode).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
