package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

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

    private final VitoServiceClient vitoClient;
    private final TshepoConsentServiceClient consentClient;

    public CitizenProfileController(VitoServiceClient vitoClient, TshepoConsentServiceClient consentClient) {
        this.vitoClient = vitoClient;
        this.consentClient = consentClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        JsonNode profile = vitoClient.getCitizenProfile(actorId);

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
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> updates) {

        JsonNode updatedProfile = vitoClient.updateCitizenProfile(actorId, updates);

        // if (updates.containsKey("phone")) {
        //     jdbcTemplate.update("UPDATE patients SET phone = ?, updated_at = ? WHERE tenant_id = ? AND cpid = ?",
        //             updates.get("phone"), now, tenantId, actorId);
        // }

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

        JsonNode consents = consentClient.listConsents(actorId, "ACTIVE", 0, 100);

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
    public ResponseEntity<Map<String, Object>> updateConsent(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        boolean granted = Boolean.TRUE.equals(body.get("granted"));

        Map<String, Object> updateRequest = new LinkedHashMap<>();
        updateRequest.put("consentId", id.toString());
        updateRequest.put("granted", granted);
        updateRequest.put("tenantId", tenantId);
        JsonNode result = consentClient.updateConsentPreference(updateRequest);

        // jdbcTemplate.update("""
        //     UPDATE consent_preferences SET granted = ?, granted_at = ?, revoked_at = NULL, updated_at = ?
        //     WHERE id = ? AND tenant_id = ?
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        vitoClient.deleteCitizenAccount(actorId);

        // jdbcTemplate.update("UPDATE patients SET status = 'DELETED', updated_at = NOW() WHERE tenant_id = ? AND cpid = ?",
        //         tenantId, actorId);

        return ResponseEntity.noContent().build();
    }
}
