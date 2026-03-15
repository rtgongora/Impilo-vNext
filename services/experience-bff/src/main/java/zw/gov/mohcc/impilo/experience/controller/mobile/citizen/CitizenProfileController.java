package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Citizen profile, consent, and account management.
 * GET    /internal/v1/mobile/citizen/profile           - get profile
 * PATCH  /internal/v1/mobile/citizen/profile           - update profile
 * GET    /internal/v1/mobile/citizen/profile/consents   - list consents
 * PATCH  /internal/v1/mobile/citizen/profile/consents/{id} - update consent
 * DELETE /internal/v1/mobile/citizen/profile/account    - delete account
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/profile")
public class CitizenProfileController {

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;

    public CitizenProfileController(JdbcTemplate jdbcTemplate, OutboxService outboxService) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, cpid, given_name, family_name, date_of_birth, sex,
                   national_id, phone, status, facility_id, created_at, updated_at
            FROM patients
            WHERE tenant_id = ? AND cpid = ?
            """, tenantId, actorId);

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Citizen profile not found for: " + actorId);
        }

        Map<String, Object> row = rows.get(0);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("cpid", row.get("cpid"));
        attributes.put("givenName", row.get("given_name"));
        attributes.put("familyName", row.get("family_name"));
        attributes.put("dateOfBirth", row.get("date_of_birth") != null ? row.get("date_of_birth").toString() : null);
        attributes.put("sex", row.get("sex"));
        attributes.put("nationalId", row.get("national_id"));
        attributes.put("phone", row.get("phone"));
        attributes.put("preferredLanguage", "en");
        attributes.put("facilityId", row.get("facility_id") != null ? row.get("facility_id").toString() : null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", row.get("id").toString(),
                "type", "CitizenProfile",
                "attributes", attributes
        ));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PatchMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> updates) {

        OffsetDateTime now = OffsetDateTime.now();
        if (updates.containsKey("phone")) {
            jdbcTemplate.update("UPDATE patients SET phone = ?, updated_at = ? WHERE tenant_id = ? AND cpid = ?",
                    updates.get("phone"), now, tenantId, actorId);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.profile-updated.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "CitizenProfile", actorId,
                Map.of("cpid", actorId, "updated_fields", updates.keySet().toString()),
                Map.of());

        return getProfile(tenantId, requestId, correlationId, actorId);
    }

    @GetMapping("/consents")
    public ResponseEntity<Map<String, Object>> listConsents(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        List<Map<String, Object>> patientRows = jdbcTemplate.queryForList(
                "SELECT id FROM patients WHERE tenant_id = ? AND cpid = ?", tenantId, actorId);
        if (patientRows.isEmpty()) {
            throw new ResourceNotFoundException("Patient not found");
        }
        UUID patientId = (UUID) patientRows.get(0).get("id");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, category, description, granted, granted_at, revoked_at
            FROM consent_preferences
            WHERE tenant_id = ? AND patient_id = ?
            ORDER BY category
            """, tenantId, patientId);

        List<Map<String, Object>> data = rows.stream().map(r -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id").toString());
            item.put("category", r.get("category"));
            item.put("description", r.get("description"));
            item.put("granted", r.get("granted"));
            item.put("grantedAt", r.get("granted_at"));
            item.put("revokedAt", r.get("revoked_at"));
            return item;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/consents/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateConsent(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        boolean granted = Boolean.TRUE.equals(body.get("granted"));
        OffsetDateTime now = OffsetDateTime.now();

        int updated;
        if (granted) {
            updated = jdbcTemplate.update("""
                UPDATE consent_preferences SET granted = TRUE, granted_at = ?, revoked_at = NULL, updated_at = ?
                WHERE id = ? AND tenant_id = ?
                """, now, now, id, tenantId);
        } else {
            updated = jdbcTemplate.update("""
                UPDATE consent_preferences SET granted = FALSE, revoked_at = ?, updated_at = ?
                WHERE id = ? AND tenant_id = ?
                """, now, now, id, tenantId);
        }

        if (updated == 0) {
            throw new ResourceNotFoundException("Consent preference not found: " + id);
        }

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.consent-updated.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "ConsentPreference", id.toString(),
                Map.of("consent_id", id.toString(), "granted", String.valueOf(granted)),
                Map.of());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, category, description, granted, granted_at, revoked_at FROM consent_preferences WHERE id = ? AND tenant_id = ?",
                id, tenantId);

        Map<String, Object> r = rows.get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", r.get("id").toString());
        data.put("category", r.get("category"));
        data.put("description", r.get("description"));
        data.put("granted", r.get("granted"));
        data.put("grantedAt", r.get("granted_at"));
        data.put("revokedAt", r.get("revoked_at"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/account")
    @Transactional
    public ResponseEntity<Void> deleteAccount(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        jdbcTemplate.update("UPDATE patients SET status = 'DELETED', updated_at = NOW() WHERE tenant_id = ? AND cpid = ?",
                tenantId, actorId);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.account-deleted.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "CitizenProfile", actorId,
                Map.of("cpid", actorId, "action", "ACCOUNT_DELETION"),
                Map.of());

        return ResponseEntity.noContent().build();
    }
}
