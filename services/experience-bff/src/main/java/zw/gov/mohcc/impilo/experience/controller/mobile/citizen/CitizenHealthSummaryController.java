package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;

import java.util.*;

/**
 * Citizen health summary endpoint — aggregated health overview.
 *
 * <p>Provides a single-call health dashboard for citizen mobile apps,
 * aggregating the citizen's key health context from canonical data sources:</p>
 * <ul>
 *   <li>Active conditions (from conditions table)</li>
 *   <li>Active allergies (from allergies table)</li>
 *   <li>Current medications (from prescriptions table)</li>
 *   <li>Latest vitals (from vitals_records table)</li>
 *   <li>Recent lab results (from lab_orders WHERE RESULTED/REVIEWED)</li>
 *   <li>Recent encounters (from encounters table)</li>
 *   <li>Upcoming telehealth sessions (from citizen_telehealth_sessions)</li>
 * </ul>
 *
 * <p>GET /internal/v1/mobile/citizen/summary</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/summary")
public class CitizenHealthSummaryController {

    private final JdbcTemplate jdbcTemplate;

    public CitizenHealthSummaryController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealthSummary(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId) {

        UUID patientId = resolvePatientId(tenantId, actorId);

        // Patient demographics
        List<Map<String, Object>> patientRows = jdbcTemplate.queryForList(
                "SELECT id, display_name, date_of_birth, gender, cpid FROM patients WHERE id = ? AND tenant_id = ?",
                patientId, tenantId);
        Map<String, Object> patient = patientRows.isEmpty() ? Map.of() : patientRows.get(0);

        // Active conditions
        List<Map<String, Object>> conditions = jdbcTemplate.queryForList("""
            SELECT id, condition_name, icd_code, severity, clinical_status, onset_date
            FROM conditions
            WHERE tenant_id = ? AND patient_id = ? AND clinical_status = 'ACTIVE'
            ORDER BY created_at DESC LIMIT 10
            """, tenantId, patientId);

        // Active allergies
        List<Map<String, Object>> allergies = jdbcTemplate.queryForList("""
            SELECT id, allergen, allergen_type, reaction, severity, status
            FROM allergies
            WHERE tenant_id = ? AND patient_id = ? AND status = 'ACTIVE'
            ORDER BY created_at DESC LIMIT 10
            """, tenantId, patientId);

        // Current medications
        List<Map<String, Object>> medications = jdbcTemplate.queryForList("""
            SELECT id, medication_name, generic_name, dosage, route, frequency,
                   duration, quantity, instructions, status, prescribed_by, dispensed_at
            FROM prescriptions
            WHERE tenant_id = ? AND patient_id = ? AND status IN ('PENDING', 'ACTIVE')
            ORDER BY created_at DESC LIMIT 10
            """, tenantId, patientId);

        // Latest vitals
        List<Map<String, Object>> vitals = jdbcTemplate.queryForList("""
            SELECT systolic, diastolic, heart_rate, temperature,
                   oxygen_saturation, respiratory_rate, weight, height,
                   pain_score, created_at
            FROM vitals_records
            WHERE tenant_id = ? AND patient_id = ?
            ORDER BY created_at DESC LIMIT 1
            """, tenantId, patientId);

        // Recent results (RESULTED or REVIEWED lab orders)
        List<Map<String, Object>> results = jdbcTemplate.queryForList("""
            SELECT id, test_name, test_code, category, status,
                   result_data, result_notes, resulted_at
            FROM lab_orders
            WHERE tenant_id = ? AND patient_id = ? AND status IN ('RESULTED', 'REVIEWED')
            ORDER BY resulted_at DESC NULLS LAST LIMIT 5
            """, tenantId, patientId);

        // Recent encounters
        List<Map<String, Object>> encounters = jdbcTemplate.queryForList("""
            SELECT id, encounter_type, status, chief_complaint,
                   started_at, ended_at, facility_id
            FROM encounters
            WHERE tenant_id = ? AND patient_id = ?
            ORDER BY created_at DESC LIMIT 5
            """, tenantId, patientId);

        // Upcoming telehealth sessions
        List<Map<String, Object>> telehealth = jdbcTemplate.queryForList("""
            SELECT id, provider_name, session_type, status,
                   scheduled_at, reason
            FROM citizen_telehealth_sessions
            WHERE tenant_id = ? AND patient_id = ? AND status IN ('REQUESTED', 'SCHEDULED', 'IN_PROGRESS')
            ORDER BY scheduled_at ASC LIMIT 5
            """, tenantId, patientId);

        // Counts for dashboard badges
        int activeConditionCount = conditions.size();
        int activeAllergyCount = allergies.size();
        int activeMedicationCount = medications.size();
        int pendingResultCount = 0;
        try {
            Long pending = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM lab_orders WHERE tenant_id = ? AND patient_id = ? AND status = 'ORDERED'",
                    Long.class, tenantId, patientId);
            pendingResultCount = pending != null ? pending.intValue() : 0;
        } catch (Exception ignored) {}

        // Assemble summary
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("patient", patient);
        summary.put("active_conditions", conditions);
        summary.put("active_allergies", allergies);
        summary.put("current_medications", medications);
        summary.put("latest_vitals", vitals.isEmpty() ? null : vitals.get(0));
        summary.put("recent_results", results);
        summary.put("recent_encounters", encounters);
        summary.put("upcoming_telehealth", telehealth);
        summary.put("counts", Map.of(
                "active_conditions", activeConditionCount,
                "active_allergies", activeAllergyCount,
                "active_medications", activeMedicationCount,
                "pending_orders", pendingResultCount,
                "upcoming_telehealth", telehealth.size()
        ));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", summary);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private UUID resolvePatientId(String tenantId, String actorId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM patients WHERE tenant_id = ? AND cpid = ?", tenantId, actorId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Patient not found for: " + actorId);
        return (UUID) rows.get(0).get("id");
    }
}
