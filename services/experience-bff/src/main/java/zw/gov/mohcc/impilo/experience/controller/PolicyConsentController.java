package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Policy consent endpoints — Privacy Policy and Terms of Use acceptance.
 *
 * <p>Records explicit user consent for versioned legal documents.
 * Consent is tracked per policy type and version so that policy updates
 * can require re-acceptance.</p>
 *
 * <ul>
 *   <li>POST /internal/v1/consent/accept — accept current policies</li>
 *   <li>GET  /internal/v1/consent/status — check consent status for current user</li>
 *   <li>GET  /internal/v1/consent/history — audit trail of consent changes</li>
 *   <li>POST /internal/v1/consent/revoke — revoke consent (triggers sign-out requirement)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/consent")
public class PolicyConsentController {

    private static final Logger log = LoggerFactory.getLogger(PolicyConsentController.class);

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public PolicyConsentController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    /**
     * Accept Privacy Policy and/or Terms of Use.
     */
    @PostMapping("/accept")
    @Transactional
    public ResponseEntity<Map<String, Object>> acceptConsent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestBody Map<String, Object> body) {

        String userId = actorId != null ? actorId : body.getOrDefault("userId", "").toString();
        String version = body.getOrDefault("version", "").toString();
        boolean privacyAccepted = Boolean.TRUE.equals(body.get("privacyPolicyAccepted"));
        boolean termsAccepted = Boolean.TRUE.equals(body.get("termsOfUseAccepted"));

        if (userId.isBlank() || version.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "userId and version are required")));
        }

        String ipAddress = forwardedFor != null ? forwardedFor.split(",")[0].trim() : null;
        OffsetDateTime now = OffsetDateTime.now();

        List<Map<String, Object>> accepted = new ArrayList<>();

        if (privacyAccepted) {
            upsertConsent(tenantId, userId, "PRIVACY_POLICY", version, now, ipAddress, userAgent);
            accepted.add(Map.of("policyType", "PRIVACY_POLICY", "version", version, "acceptedAt", now.toString()));
        }

        if (termsAccepted) {
            upsertConsent(tenantId, userId, "TERMS_OF_USE", version, now, ipAddress, userAgent);
            accepted.add(Map.of("policyType", "TERMS_OF_USE", "version", version, "acceptedAt", now.toString()));
        }

        // Publish audit event
        outboxService.publish(
                "impilo.experience.consent.policy-accepted.v1",
                UUID.randomUUID().toString(),
                Map.of(
                        "userId", userId,
                        "tenantId", tenantId,
                        "version", version,
                        "privacyPolicyAccepted", privacyAccepted,
                        "termsOfUseAccepted", termsAccepted,
                        "timestamp", now.toString()
                ));

        log.info("Policy consent accepted: user={}, version={}, privacy={}, terms={}",
                userId, version, privacyAccepted, termsAccepted);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", userId,
                "type", "policy_consent",
                "attributes", Map.of(
                        "accepted", accepted,
                        "version", version
                )
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Check consent status for the current user.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConsentStatus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(required = false) String version) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        String sql;
        List<Map<String, Object>> records;

        if (version != null && !version.isBlank()) {
            sql = "SELECT id, policy_type, policy_version, accepted, accepted_at, revoked_at " +
                  "FROM policy_consent WHERE tenant_id = ? AND user_id = ? AND policy_version = ? " +
                  "ORDER BY policy_type";
            records = jdbcTemplate.queryForList(sql, tenantId, actorId, version);
        } else {
            sql = "SELECT DISTINCT ON (policy_type) id, policy_type, policy_version, accepted, accepted_at, revoked_at " +
                  "FROM policy_consent WHERE tenant_id = ? AND user_id = ? " +
                  "ORDER BY policy_type, policy_version DESC";
            records = jdbcTemplate.queryForList(sql, tenantId, actorId);
        }

        List<Map<String, Object>> data = records.stream().map(r -> Map.<String, Object>of(
                "id", r.get("id").toString(),
                "type", "policy_consent",
                "attributes", Map.of(
                        "policyType", r.get("policy_type"),
                        "policyVersion", r.get("policy_version"),
                        "accepted", r.get("accepted"),
                        "acceptedAt", r.get("accepted_at") != null ? r.get("accepted_at").toString() : "",
                        "revokedAt", r.get("revoked_at") != null ? r.get("revoked_at").toString() : ""
                )
        )).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Consent history — audit trail of all consent changes for the current user.
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getConsentHistory(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }

        String sql = "SELECT id, policy_type, policy_version, accepted, accepted_at, revoked_at, created_at " +
                     "FROM policy_consent WHERE tenant_id = ? AND user_id = ? " +
                     "ORDER BY created_at DESC";

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, tenantId, actorId);

        List<Map<String, Object>> data = records.stream().map(r -> Map.<String, Object>of(
                "id", r.get("id").toString(),
                "type", "policy_consent_history",
                "attributes", Map.of(
                        "policyType", r.get("policy_type"),
                        "policyVersion", r.get("policy_version"),
                        "accepted", r.get("accepted"),
                        "acceptedAt", r.get("accepted_at") != null ? r.get("accepted_at").toString() : "",
                        "revokedAt", r.get("revoked_at") != null ? r.get("revoked_at").toString() : "",
                        "createdAt", r.get("created_at").toString()
                )
        )).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    /**
     * Revoke consent — marks existing consent as revoked.
     */
    @PostMapping("/revoke")
    @Transactional
    public ResponseEntity<Map<String, Object>> revokeConsent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {

        String userId = actorId != null ? actorId : body.getOrDefault("userId", "").toString();
        String policyType = body.getOrDefault("policyType", "").toString();

        if (userId.isBlank() || policyType.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "userId and policyType are required")));
        }

        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update(
                "UPDATE policy_consent SET accepted = FALSE, revoked_at = ?, updated_at = ? " +
                "WHERE tenant_id = ? AND user_id = ? AND policy_type = ? AND accepted = TRUE",
                now, now, tenantId, userId, policyType);

        outboxService.publish(
                "impilo.experience.consent.policy-revoked.v1",
                UUID.randomUUID().toString(),
                Map.of(
                        "userId", userId,
                        "tenantId", tenantId,
                        "policyType", policyType,
                        "timestamp", now.toString()
                ));

        log.info("Policy consent revoked: user={}, policyType={}", userId, policyType);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", userId,
                "type", "policy_consent_revocation",
                "attributes", Map.of(
                        "policyType", policyType,
                        "revokedAt", now.toString()
                )
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void upsertConsent(String tenantId, String userId, String policyType,
                                String version, OffsetDateTime now,
                                String ipAddress, String userAgent) {
        jdbcTemplate.update(
                "INSERT INTO policy_consent (id, tenant_id, user_id, policy_type, policy_version, " +
                "accepted, accepted_at, ip_address, user_agent, created_at, updated_at) " +
                "VALUES (gen_random_uuid(), ?, ?, ?, ?, TRUE, ?, ?, ?, NOW(), NOW()) " +
                "ON CONFLICT (tenant_id, user_id, policy_type, policy_version) " +
                "DO UPDATE SET accepted = TRUE, accepted_at = ?, revoked_at = NULL, " +
                "ip_address = ?, user_agent = ?, updated_at = NOW()",
                tenantId, userId, policyType, version, now, ipAddress, userAgent,
                now, ipAddress, userAgent);
    }
}
