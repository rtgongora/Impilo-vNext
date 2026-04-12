package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient;
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
    private final VitoServiceClient vitoClient;
    private final TshepoConsentServiceClient consentClient;

    public CitizenProfileController(JdbcTemplate jdbcTemplate, OutboxService outboxService,
                                    VitoServiceClient vitoClient, TshepoConsentServiceClient consentClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.vitoClient = vitoClient;
        this.consentClient = consentClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        // STRANGLER: migrated — delegate to VITO for citizen profile
        JsonNode profile = vitoClient.getCitizenProfile(actorId);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from patients table
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        //     SELECT id, cpid, given_name, family_name, date_of_birth, sex,
        //            national_id, phone, status, facility_id, created_at, updated_at
        //     FROM patients WHERE tenant_id = ? AND cpid = ?
        //     """, tenantId, actorId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", profile);
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

        // STRANGLER: migrated — delegate to VITO
        JsonNode updatedProfile = vitoClient.updateCitizenProfile(actorId, updates);

        // STRANGLER: migrated — was direct JdbcTemplate UPDATE on patients table
        // if (updates.containsKey("phone")) {
        //     jdbcTemplate.update("UPDATE patients SET phone = ?, updated_at = ? WHERE tenant_id = ? AND cpid = ?",
        //             updates.get("phone"), now, tenantId, actorId);
        // }

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.profile-updated.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "CitizenProfile", actorId,
                Map.of("cpid", actorId, "updated_fields", updates.keySet().toString()),
                Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", updatedProfile);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/consents")
    public ResponseEntity<Map<String, Object>> listConsents(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        // STRANGLER: migrated — delegate to TSHEPO Consent service
        JsonNode consents = consentClient.listConsents(actorId, "ACTIVE", 0, 100);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from consent_preferences table
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        //     SELECT id, category, description, granted, granted_at, revoked_at
        //     FROM consent_preferences WHERE tenant_id = ? AND patient_id = ? ORDER BY category
        //     """, tenantId, patientId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", consents);
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

        // STRANGLER: migrated — delegate to TSHEPO Consent service
        Map<String, Object> updateRequest = new LinkedHashMap<>();
        updateRequest.put("consentId", id.toString());
        updateRequest.put("granted", granted);
        updateRequest.put("tenantId", tenantId);
        JsonNode result = consentClient.updateConsentPreference(updateRequest);

        // STRANGLER: migrated — was direct JdbcTemplate UPDATE on consent_preferences table
        // jdbcTemplate.update("""
        //     UPDATE consent_preferences SET granted = ?, granted_at = ?, revoked_at = NULL, updated_at = ?
        //     WHERE id = ? AND tenant_id = ?
        //     """, ...);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.consent-updated.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "ConsentPreference", id.toString(),
                Map.of("consent_id", id.toString(), "granted", String.valueOf(granted)),
                Map.of());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
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

        // STRANGLER: migrated — delegate to VITO
        vitoClient.deleteCitizenAccount(actorId);

        // STRANGLER: migrated — was direct JdbcTemplate UPDATE setting status='DELETED'
        // jdbcTemplate.update("UPDATE patients SET status = 'DELETED', updated_at = NOW() WHERE tenant_id = ? AND cpid = ?",
        //         tenantId, actorId);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.account-deleted.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "CitizenProfile", actorId,
                Map.of("cpid", actorId, "action", "ACCOUNT_DELETION"),
                Map.of());

        return ResponseEntity.noContent().build();
    }
}
