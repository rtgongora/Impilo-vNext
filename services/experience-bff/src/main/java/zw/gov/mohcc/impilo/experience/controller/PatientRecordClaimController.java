package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Claim my existing Impilo record" (Identity Journey Doctrine §2, CJ3).
 *
 * <p>The load-bearing security flow: an authenticated account-shell submits an
 * identity <b>claim</b>; the Trust Core resolves it <b>privately</b> (no candidate
 * list, no existence signal); a second-factor OTP is dispatched to a contact
 * <b>already recorded</b> on the matched record; only on verified possession is the
 * account bound to the Health ID (RECORD_LINKED). Knowing someone's details is
 * never enough — the code lands at the record's own contact.</p>
 *
 * <p>Anti-enumeration: {@code start} always returns the same shape (a challengeId +
 * a generic "a code was sent if the record exists" hint). A claim that resolves to
 * nothing, or to a record with no deliverable contact, yields a dummy challenge that
 * can never verify. The client never learns whether a record exists.</p>
 */
@RestController
@RequestMapping("/internal/v1/identity/patient/claim")
public class PatientRecordClaimController {

    private static final Logger log = LoggerFactory.getLogger(PatientRecordClaimController.class);

    private final TshepoIdentityServiceClient tshepoIdentity;
    private final VitoServiceClient vito;
    private final NotificationServiceClient notification;

    public PatientRecordClaimController(TshepoIdentityServiceClient tshepoIdentity,
                                        VitoServiceClient vito,
                                        NotificationServiceClient notification) {
        this.tshepoIdentity = tshepoIdentity;
        this.vito = vito;
        this.notification = notification;
    }

    public record ClaimStartRequest(String claimKind, String claimValue) {}
    public record ClaimVerifyRequest(String challengeId, String code) {}

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(
            @RequestBody ClaimStartRequest req,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {

        Map<String, Object> generic = new LinkedHashMap<>();
        generic.put("status", "VERIFICATION_REQUIRED");
        generic.put("message", "If a matching record exists, a verification code has been sent to the "
                + "contact registered on that record.");

        try {
            // 1. Private, server-side resolution — never surfaced to the client.
            JsonNode resolved = tshepoIdentity.resolveIdentifier(Map.of(
                    "tenantId", tenantId, "kind", safeUpper(req.claimKind()), "value", nullSafe(req.claimValue())));
            String healthId = resolved != null && resolved.path("resolved").asBoolean(false)
                    ? text(resolved.path("personRef"), "healthId") : null;

            if (healthId == null) {
                // Uniform response with a dummy challenge that can never verify.
                generic.put("challengeId", UUID.randomUUID().toString());
                return ResponseEntity.ok(generic);
            }

            // 2. Issue a second factor to the recorded contact.
            JsonNode ch = vito.claimStart(Map.of("tenantId", tenantId, "healthId", healthId));
            String challengeId = text(ch, "challengeId");
            boolean deliverable = ch != null && ch.path("deliverable").asBoolean(false);
            if (deliverable) {
                deliverOtp(text(ch, "channel"), text(ch, "contact"), text(ch, "otp"));
            }
            generic.put("challengeId", challengeId != null ? challengeId : UUID.randomUUID().toString());
            String hint = text(ch, "maskedContact");
            if (hint != null) {
                generic.put("hint", hint);
            }
            return ResponseEntity.ok(generic);
        } catch (Exception e) {
            // Never leak via error shape — return the uniform response.
            log.debug("claim/start suppressed error (anti-enum): {}", e.getMessage());
            generic.put("challengeId", UUID.randomUUID().toString());
            return ResponseEntity.ok(generic);
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestBody ClaimVerifyRequest req,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String accountRef) {

        Map<String, Object> out = new LinkedHashMap<>();
        try {
            JsonNode v = vito.claimVerify(Map.of(
                    "challengeId", nullSafe(req.challengeId()),
                    "code", nullSafe(req.code()),
                    "accountRef", accountRef != null ? accountRef : ""));
            boolean verified = v != null && v.path("verified").asBoolean(false);
            // Anti-abuse: if this attempt locked the challenge, warn the record owner at their
            // recorded contact. The owner's contact is consumed here (trust core) and never
            // echoed back to the claimant — the client response stays generic.
            if (v != null && v.path("securityAlert").asBoolean(false)) {
                deliverOwnerAlert(text(v, "ownerChannel"), text(v, "ownerContact"));
            }
            out.put("status", verified ? "LINKED" : "VERIFICATION_FAILED");
            if (verified) {
                out.put("healthId", text(v, "healthId"));
                out.put("assurance", "RECORD_LINKED");
                out.put("message", "Your existing Impilo record has been securely linked to your account.");
            } else {
                out.put("message", "Verification could not be completed. Please try again or use assisted verification.");
            }
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.debug("claim/verify suppressed error: {}", e.getMessage());
            out.put("status", "VERIFICATION_FAILED");
            out.put("message", "Verification could not be completed. Please try again or use assisted verification.");
            return ResponseEntity.ok(out);
        }
    }

    private void deliverOtp(String channel, String contact, String otp) {
        if (contact == null || otp == null) {
            return;
        }
        boolean sms = !"email".equalsIgnoreCase(channel);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateKey", sms ? "RECORD_CLAIM_OTP" : "RECORD_CLAIM_OTP_EMAIL");
        body.put("channel", sms ? "SMS" : "EMAIL");
        body.put("to", contact);
        body.put("messageKind", "SENSITIVE");
        body.put("variables", Map.of("otp", otp));
        try {
            notification.sendNotification(body);
        } catch (Exception e) {
            log.warn("record-claim OTP delivery not accepted: {}", e.getMessage());
        }
    }

    /** Warn the record owner that someone attempted (and failed) to claim their record. */
    private void deliverOwnerAlert(String channel, String contact) {
        if (contact == null || contact.isBlank()) {
            return;
        }
        boolean sms = !"email".equalsIgnoreCase(channel);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateKey", sms ? "RECORD_CLAIM_SECURITY_ALERT" : "RECORD_CLAIM_SECURITY_ALERT_EMAIL");
        body.put("channel", sms ? "SMS" : "EMAIL");
        body.put("to", contact);
        body.put("messageKind", "SENSITIVE");
        try {
            notification.sendNotification(body);
        } catch (Exception e) {
            log.warn("record-claim owner alert not accepted: {}", e.getMessage());
        }
    }

    private static String safeUpper(String s) { return s == null ? "" : s.trim().toUpperCase(); }
    private static String nullSafe(String s) { return s == null ? "" : s; }
    private static String text(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }
}
