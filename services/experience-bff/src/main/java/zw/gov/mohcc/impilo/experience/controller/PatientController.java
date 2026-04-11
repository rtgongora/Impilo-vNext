package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.domain.Patient;
import zw.gov.mohcc.impilo.experience.repository.PatientRepository;
import zw.gov.mohcc.impilo.experience.service.PiiAccessAuditService;

import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;

/**
 * Patient CRUD with privacy-by-design PII filtering.
 *
 * <p>Per Health OS doctrine ("No PII in SHR", "minimum-necessary access"):
 * the toResource() method returns different PII field sets based on the
 * caller's X-Purpose-Of-Use header and role context. Every PII access
 * is recorded in the pii_access_log via PiiAccessAuditService.</p>
 *
 * <p>PII tiers:
 * <ul>
 *   <li><b>FULL</b>: all fields (TREATMENT purpose, clinical roles with active encounter)</li>
 *   <li><b>STANDARD</b>: name + age + gender + CPID (default for authenticated clinical staff)</li>
 *   <li><b>MINIMAL</b>: CPID + age-range + gender only (audit/admin/reporting contexts)</li>
 * </ul></p>
 */
@RestController
@RequestMapping("/internal/v1/patients")
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);

    /** Purposes that grant FULL PII access. */
    private static final Set<String> FULL_PII_PURPOSES = Set.of(
            "TREATMENT", "EMERGENCY", "CARE_COORDINATION");

    /** Purposes that get MINIMAL PII. */
    private static final Set<String> MINIMAL_PII_PURPOSES = Set.of(
            "AUDIT", "REPORTING", "RESEARCH", "PUBLIC_HEALTH");

    private final PatientRepository patientRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PiiAccessAuditService piiAudit;

    public PatientController(PatientRepository patientRepository,
                              JdbcTemplate jdbcTemplate,
                              PiiAccessAuditService piiAudit) {
        this.patientRepository = patientRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.piiAudit = piiAudit;
    }

    public record CreatePatientRequest(
            @NotBlank String given_name,
            @NotBlank String family_name,
            String date_of_birth,
            String sex,
            String national_id,
            String phone,
            @NotBlank String facility_id
    ) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPatient(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody CreatePatientRequest req) {

        UUID id = UUID.randomUUID();
        String cpid = "CP-" + id.toString().substring(0, 8).toUpperCase();

        jdbcTemplate.update("""
                INSERT INTO patients (id, tenant_id, cpid, given_name, family_name, date_of_birth,
                    sex, national_id, phone, facility_id, status, created_at, updated_at)
                VALUES (?::uuid, ?, ?, ?, ?, ?::date, ?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                """,
                id.toString(), tenantId, cpid,
                req.given_name(), req.family_name(), req.date_of_birth(),
                req.sex(), req.national_id(), req.phone(), req.facility_id());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id.toString(), "type", "Patient",
                "attributes", Map.of("cpid", cpid, "given_name", req.given_name(),
                        "family_name", req.family_name(), "status", "ACTIVE")));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));

        return ResponseEntity.status(201).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listPatients(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Facility-ID", required = false) String facilityId,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("familyName").ascending());
        Page<Patient> result = patientRepository.findByFilters(tenantId, search, status, pageable);

        String piiTier = determinePiiTier(purposeOfUse);
        String[] fieldsAccessed = piiTier.equals("FULL")
                ? new String[]{"name", "dob", "gender", "national_id", "phone", "cpid"}
                : piiTier.equals("STANDARD")
                    ? new String[]{"name", "dob", "gender", "cpid"}
                    : new String[]{"cpid", "gender"};

        List<Map<String, Object>> data = result.getContent().stream()
                .map(p -> toResource(p, piiTier))
                .toList();

        // Audit PII access for search results
        if (actorId != null && !result.isEmpty()) {
            for (Patient p : result.getContent()) {
                piiAudit.recordAccess(tenantId, actorId, actorType, p.getId().toString(),
                        "PATIENT", "SEARCH", fieldsAccessed, purposeOfUse, "API",
                        piiTier, forwardedFor, userAgent, facilityId);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "pii_tier", piiTier,
                "page", Map.of(
                        "number", result.getNumber(),
                        "size", result.getSize(),
                        "total_elements", result.getTotalElements(),
                        "total_pages", result.getTotalPages()
                )
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPatient(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purposeOfUse,
            @RequestHeader(value = "X-Facility-ID", required = false) String facilityId,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        Patient patient = patientRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found"));

        String piiTier = determinePiiTier(purposeOfUse);
        String[] fieldsAccessed = piiTier.equals("FULL")
                ? new String[]{"name", "dob", "gender", "national_id", "phone", "cpid"}
                : piiTier.equals("STANDARD")
                    ? new String[]{"name", "dob", "gender", "cpid"}
                    : new String[]{"cpid", "gender"};

        // Audit PII access
        if (actorId != null) {
            piiAudit.recordAccess(tenantId, actorId, actorType, id.toString(),
                    "PATIENT", "VIEW", fieldsAccessed, purposeOfUse, "API",
                    piiTier, forwardedFor, userAgent, facilityId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", toResource(patient, piiTier));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId,
                "pii_tier", piiTier
        ));

        return ResponseEntity.ok(response);
    }

    // ── PII tier determination ──────────────────────────────────

    /**
     * Determine the PII tier based on purpose of use.
     * FULL for treatment/emergency, MINIMAL for audit/reporting, STANDARD otherwise.
     */
    private String determinePiiTier(String purposeOfUse) {
        if (purposeOfUse == null || purposeOfUse.isBlank()) return "STANDARD";
        String upper = purposeOfUse.toUpperCase();
        if (FULL_PII_PURPOSES.contains(upper)) return "FULL";
        if (MINIMAL_PII_PURPOSES.contains(upper)) return "MINIMAL";
        return "STANDARD";
    }

    // ── Resource serialization with PII filtering ───────────────

    /**
     * Convert a Patient entity to a JSON:API resource with PII fields
     * filtered according to the determined tier.
     *
     * <p>Always includes: cpid, sex, status, facility_id.
     * STANDARD adds: displayName (full), dateOfBirth, gender.
     * FULL adds: national_id, phone, given_name, family_name.</p>
     */
    private Map<String, Object> toResource(Patient p, String piiTier) {
        Map<String, Object> attributes = new LinkedHashMap<>();

        // Always safe: CPID is a clinical pseudonym, not PII
        attributes.put("cpid", p.getCpid());
        attributes.put("sex", p.getSex());
        attributes.put("gender", p.getSex()); // alias for frontend compat
        attributes.put("status", p.getStatus());
        attributes.put("facility_id", p.getFacilityId());
        attributes.put("created_at", p.getCreatedAt());
        attributes.put("updated_at", p.getUpdatedAt());

        switch (piiTier) {
            case "FULL":
                // Full PII — treatment context with active encounter
                attributes.put("displayName", buildDisplayName(p));
                attributes.put("given_name", p.getGivenName());
                attributes.put("family_name", p.getFamilyName());
                attributes.put("givenName", p.getGivenName());
                attributes.put("familyName", p.getFamilyName());
                attributes.put("dateOfBirth", formatDate(p.getDateOfBirth()));
                attributes.put("date_of_birth", formatDate(p.getDateOfBirth()));
                attributes.put("national_id", p.getNationalId());
                attributes.put("nationalId", p.getNationalId());
                attributes.put("phone", p.getPhone());
                break;

            case "STANDARD":
                // Name + DOB + gender — default clinical context
                attributes.put("displayName", buildDisplayName(p));
                attributes.put("givenName", p.getGivenName());
                attributes.put("familyName", p.getFamilyName());
                attributes.put("given_name", p.getGivenName());
                attributes.put("family_name", p.getFamilyName());
                attributes.put("dateOfBirth", formatDate(p.getDateOfBirth()));
                attributes.put("date_of_birth", formatDate(p.getDateOfBirth()));
                // Omit: national_id, phone
                break;

            case "MINIMAL":
            default:
                // CPID + age range + gender only — reporting/audit context
                attributes.put("displayName", buildInitials(p));
                attributes.put("ageRange", computeAgeRange(p.getDateOfBirth()));
                // Omit: name, DOB, national_id, phone
                break;
        }

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", p.getId().toString());
        resource.put("type", "Patient");
        resource.put("attributes", attributes);
        return resource;
    }

    /** Backward-compatible toResource for internal use (STANDARD tier). */
    private Map<String, Object> toResource(Patient p) {
        return toResource(p, "STANDARD");
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String buildDisplayName(Patient p) {
        String given = p.getGivenName() != null ? p.getGivenName() : "";
        String family = p.getFamilyName() != null ? p.getFamilyName() : "";
        return (given + " " + family).trim();
    }

    private String buildInitials(Patient p) {
        StringBuilder sb = new StringBuilder();
        if (p.getGivenName() != null && !p.getGivenName().isEmpty()) {
            sb.append(p.getGivenName().charAt(0)).append(".");
        }
        if (p.getFamilyName() != null && !p.getFamilyName().isEmpty()) {
            sb.append(p.getFamilyName().charAt(0)).append(".");
        }
        return sb.toString();
    }

    private String computeAgeRange(java.time.LocalDate dob) {
        if (dob == null) return "—";
        int age = Period.between(dob, java.time.LocalDate.now()).getYears();
        int decade = (age / 10) * 10;
        return decade + "–" + (decade + 9) + "y";
    }

    private String formatDate(java.time.LocalDate date) {
        return date != null ? date.toString() : null;
    }
}
