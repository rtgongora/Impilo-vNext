package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.madi.core.DonorService;
import zw.gov.mohcc.impilo.madi.domain.ScreeningResult;
import zw.gov.mohcc.impilo.madi.integration.NdilaIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/madi/donors")
public class MadiDonorController {

    private final DonorService donorService;
    private final NdilaIntegration ndilaIntegration;

    public MadiDonorController(DonorService donorService, NdilaIntegration ndilaIntegration) {
        this.donorService = donorService;
        this.ndilaIntegration = ndilaIntegration;
    }

    @PostMapping
    public ResponseEntity<DonorProfileEntity> register(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(donorService.register(
                tenantId,
                required(body, "person_cpid"),
                str(body, "blood_group"),
                str(body, "rh_factor"),
                facilityId,
                str(body, "registered_by")));
    }

    @GetMapping("/by-person")
    public ResponseEntity<DonorProfileEntity> byPerson(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam("person_cpid") String personCpid) {
        return donorService.findByPersonCpid(tenantId, personCpid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{donorId}/screenings")
    public ResponseEntity<DonorEligibilityScreeningEntity> screen(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID donorId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(donorService.screen(
                tenantId, donorId, uuid(body, "drive_id"),
                ScreeningResult.valueOf(required(body, "result")),
                str(body, "notes"), str(body, "screened_by"), facilityId));
    }

    @PostMapping("/{donorId}/deferrals")
    public ResponseEntity<DonorDeferralEntity> defer(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID donorId,
            @RequestBody Map<String, Object> body) {
        LocalDate until = body.get("deferred_until") != null
                ? LocalDate.parse(body.get("deferred_until").toString()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(donorService.defer(
                tenantId, donorId, required(body, "reason_code"), until,
                str(body, "notes"), str(body, "deferred_by"), facilityId));
    }

    @PutMapping("/{donorId}/preferences")
    public DonorCommunicationPreferenceEntity preferences(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID donorId,
            @RequestBody Map<String, Object> body) {
        return donorService.updatePreferences(tenantId, donorId,
                required(body, "channel"),
                Boolean.parseBoolean(body.getOrDefault("enabled", "true").toString()),
                str(body, "locale"), str(body, "updated_by"), facilityId);
    }

    @PostMapping("/{donorId}/feedback")
    public ResponseEntity<DonorFeedbackEntity> feedback(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) UUID facilityId,
            @PathVariable UUID donorId,
            @RequestBody Map<String, Object> body) {
        Integer rating = body.get("rating") != null ? Integer.parseInt(body.get("rating").toString()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(donorService.submitFeedback(
                tenantId, donorId, uuid(body, "drive_id"), rating, str(body, "comments"), facilityId));
    }

    @GetMapping("/{donorId}/history")
    public Map<String, Object> history(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID donorId) {
        return donorService.donorHistory(tenantId, donorId);
    }

    @GetMapping("/{donorId}/next-eligibility")
    public Map<String, Object> nextEligibility(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID donorId) {
        return donorService.nextEligibility(tenantId, donorId);
    }

    @GetMapping("/drives-near-me")
    public List<Map<String, Object>> drivesNearMe(
            @RequestParam("ndila_site_ref") String ndilaSiteRef,
            @RequestParam(value = "radius_km", defaultValue = "25") double radiusKm) {
        return ndilaIntegration.findDrivesNearSite(ndilaSiteRef, radiusKm);
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null && !v.toString().isBlank() ? UUID.fromString(v.toString()) : null;
    }
}
