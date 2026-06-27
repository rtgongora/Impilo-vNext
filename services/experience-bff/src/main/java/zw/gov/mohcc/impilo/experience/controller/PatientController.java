package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.time.LocalDate;
import java.util.*;

/**
 * Patient / client directory for walk-in and shell flows — pure proxy to the VITO
 * client registry and identity issuance.
 *
 * <p>VITO is the system of record for patient identity (PII stays in VITO per
 * doctrine). This stateless BFF NEVER fabricates patient identities: when VITO is
 * unavailable it fails clean ({@code 503 VITO_UNAVAILABLE}) rather than minting a
 * phantom CPID or serving a seeded directory. (The former in-memory seeded
 * {@code PATIENTS} fallback was removed — a composition layer must not be a source
 * of truth for who exists.)</p>
 */
@RestController
@RequestMapping("/internal/v1/patients")
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);
    private final VitoServiceClient vitoClient;

    public PatientController(VitoServiceClient vitoClient) {
        this.vitoClient = vitoClient;
    }

    private ResponseEntity<Map<String, Object>> vitoUnavailable(String operation, String reason,
                                                                String requestId, String correlationId) {
        log.warn("VITO UNAVAILABLE — operation={}, reason={}, requestId={} — returning 503 "
                + "(BFF never fabricates patient identity)", operation, reason, requestId);
        return ResponseEntity.status(503).body(Map.of(
                "error", Map.of("code", "VITO_UNAVAILABLE",
                        "message", "The patient registry is temporarily unavailable. Please try again shortly."),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPatient(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityHeader,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> patientData = new LinkedHashMap<>(body);
        if ((!patientData.containsKey("facility_id") || patientData.get("facility_id") == null)
                && facilityHeader != null && !facilityHeader.isBlank()) {
            patientData.put("facility_id", facilityHeader);
        }

        String givenName = strVal(patientData, "given_name", "givenName", "firstName");
        String familyName = strVal(patientData, "family_name", "familyName", "lastName");
        String displayName = strVal(patientData, "displayName", "display_name");
        String dob = strVal(patientData, "date_of_birth", "dateOfBirth");
        String sex = strVal(patientData, "sex", "gender");
        String nationalId = strVal(patientData, "national_id", "nationalId");
        String phone = strVal(patientData, "phone");
        String facilityId = strVal(patientData, "facility_id", "facilityId");

        if ((givenName == null || givenName.isBlank()) && displayName != null) {
            String[] parts = displayName.trim().split("\\s+", 2);
            givenName = parts[0];
            familyName = parts.length > 1 ? parts[1] : "";
        }
        if (givenName == null || givenName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "Patient name is required")));
        }

        try {
            Map<String, Object> registryBody = toClientRegistryRegistration(patientData);
            JsonNode profile = vitoClient.createClientRegistration(registryBody);
            Map<String, Object> patient = mapClientProfileToPatient(profile);
            patient = withRegistrationOverlay(patient, patientData, true);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", patient);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            log.info("VITO client-registry registration unavailable — trying legacy issuance path: {}", e.getMessage());
        }

        try {
            Map<String, Object> vitoPayload = new LinkedHashMap<>(patientData);
            vitoPayload.put("given_name", givenName);
            vitoPayload.put("family_name", familyName != null ? familyName : "");
            vitoPayload.put("date_of_birth", dob);
            vitoPayload.put("sex", sex);
            vitoPayload.put("national_id", nationalId);
            vitoPayload.put("phone", phone);
            vitoPayload.put("facility_id", facilityId);
            vitoPayload.put("tenant_id", tenantId);

            JsonNode result = vitoClient.registerPatient(vitoPayload);
            Map<String, Object> patient = mapIssuanceToPatient(result, givenName,
                    familyName != null ? familyName : "", dob, sex, nationalId, phone);
            patient = withRegistrationOverlay(patient, patientData, true);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", patient);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            log.info("VITO legacy issuance unavailable: {}", e.getMessage());
        }

        // Both VITO registration paths failed. Never mint a phantom CPID in the BFF —
        // a fabricated patient identity is a patient-safety hazard. Fail clean.
        return vitoUnavailable("createPatient", "both VITO registration paths failed",
                requestId, correlationId);
    }

    /**
     * Masked client search (search-before-create) — proxies VITO internal search.
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchPatients(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = vitoClient.searchInternalClients(body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("VITO client search failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of("candidates", List.of()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId, "warning", e.getMessage())));
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listPatients(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {

        try {
            JsonNode paged = vitoClient.listClientRegistryClients(search, status, null, page, size);
            List<Map<String, Object>> mapped = new ArrayList<>();
            if (paged != null && paged.has("items") && paged.get("items").isArray()) {
                for (JsonNode item : paged.get("items")) {
                    mapped.add(mapRegistrySummaryToPatient(item));
                }
            }
            long total = paged != null && paged.has("totalElements") ? paged.get("totalElements").asLong() : mapped.size();
            // Return the real VITO result — including a legitimately empty page (no matches).
            // Never substitute a seeded directory for an empty/failed registry query.
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", mapped);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                    "page", Map.of("number", page, "size", size, "total_elements", total)));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // VITO is the registry of record — on outage fail clean rather than serve a seeded list.
            return vitoUnavailable("listPatients", "VITO client-registry list failed: "
                    + e.getClass().getSimpleName(), requestId, correlationId);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPatient(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        boolean upstreamErrored = false;
        try {
            JsonNode profile = vitoClient.getClientRegistryProfile(id);
            if (profile != null) {
                Map<String, Object> patient = mapClientProfileToPatient(profile);
                return ResponseEntity.ok(Map.of(
                        "data", patient,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception e) {
            upstreamErrored = true;
            log.debug("VITO client profile error for id={}: {}", id, e.getMessage());
        }

        try {
            JsonNode entity = vitoClient.getPatient(id);
            if (entity != null) {
                Map<String, Object> patient = mapClientEntityToPatient(entity);
                return ResponseEntity.ok(Map.of(
                        "data", patient,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception e) {
            upstreamErrored = true;
            log.debug("VITO getPatient error for id={}: {}", id, e.getMessage());
        }

        // Distinguish "registry is down" (fail clean, retryable) from "no such patient".
        if (upstreamErrored) {
            return vitoUnavailable("getPatient", "VITO lookup failed for id=" + id, requestId, correlationId);
        }
        return ResponseEntity.status(404).body(Map.of(
                "error", Map.of("code", "PATIENT_NOT_FOUND", "message", "Patient not found"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /**
     * Copies registry-template / workflow metadata onto the patient envelope returned to the Experience Layer.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withRegistrationOverlay(
            Map<String, Object> patient,
            Map<String, Object> request,
            boolean delegatedToVito) {
        Map<String, Object> out = new LinkedHashMap<>(patient);
        Map<String, Object> attrs = new LinkedHashMap<>((Map<String, Object>) patient.get("attributes"));
        copyIfPresent(attrs, request, "registration_mode");
        copyIfPresent(attrs, request, "initiating_actor");
        copyIfPresent(attrs, request, "initiating_context");
        copyIfPresent(attrs, request, "assurance_level");
        copyIfPresent(attrs, request, "identity_state");
        copyIfPresent(attrs, request, "offline_provisional");
        copyIfPresent(attrs, request, "consent_status");
        copyIfPresent(attrs, request, "consent_deferred_reason");
        copyIfPresent(attrs, request, "purpose_of_use");
        copyIfPresent(attrs, request, "country_alpha2");
        copyIfPresent(attrs, request, "province_code");
        copyIfPresent(attrs, request, "district_code");
        copyIfPresent(attrs, request, "ward_code");
        copyIfPresent(attrs, request, "locality_gazetteer_id");
        copyIfPresent(attrs, request, "locality_proposal_text");
        copyIfPresent(attrs, request, "coverage");
        copyIfPresent(attrs, request, "middle_name", "middleName");
        copyIfPresent(attrs, request, "email");
        copyIfPresent(attrs, request, "passport_reference", "passportReference");
        copyIfPresent(attrs, request, "address_line", "addressLine1");
        copyIfPresent(attrs, request, "city");
        copyIfPresent(attrs, request, "preferred_language", "preferredLanguage");
        copyIfPresent(attrs, request, "marital_status", "maritalStatus");
        copyIfPresent(attrs, request, "emergency_contact_name", "emergencyContactName");
        copyIfPresent(attrs, request, "emergency_contact_phone", "emergencyContactPhone");
        attrs.put("registryDelegation", delegatedToVito);
        attrs.put("registrySyncState", delegatedToVito ? "VITO_ISSUED" : "OFFLINE_PROVISIONAL_LOCAL_FALLBACK");
        out.put("attributes", attrs);
        return out;
    }

    private static void copyIfPresent(Map<String, Object> attrs, Map<String, Object> req, String key) {
        if (req.containsKey(key) && req.get(key) != null) {
            attrs.put(key, req.get(key));
        }
    }

    private static void copyIfPresent(Map<String, Object> attrs, Map<String, Object> req, String reqKey, String attrKey) {
        if (req.containsKey(reqKey) && req.get(reqKey) != null) {
            attrs.put(attrKey, req.get(reqKey));
        }
    }

    /**
     * Maps Experience walk-in / wizard patient payloads onto Vito client-registry registration bodies.
     */
    static Map<String, Object> toClientRegistryRegistration(Map<String, Object> body) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        copyIfPresent(metadata, body, "registration_mode");
        copyIfPresent(metadata, body, "initiating_actor");
        copyIfPresent(metadata, body, "initiating_context");
        copyIfPresent(metadata, body, "assurance_level");
        copyIfPresent(metadata, body, "identity_state");
        copyIfPresent(metadata, body, "offline_provisional");
        copyIfPresent(metadata, body, "consent_status");
        copyIfPresent(metadata, body, "consent_deferred_reason");
        copyIfPresent(metadata, body, "purpose_of_use");
        copyIfPresent(metadata, body, "country_alpha2");
        copyIfPresent(metadata, body, "province_code");
        copyIfPresent(metadata, body, "district_code");
        copyIfPresent(metadata, body, "ward_code");
        copyIfPresent(metadata, body, "locality_gazetteer_id");
        copyIfPresent(metadata, body, "locality_proposal_text");
        copyIfPresent(metadata, body, "coverage");
        copyIfPresent(metadata, body, "dob_certainty");
        copyIfPresent(metadata, body, "unknown_name_flag");

        Map<String, Object> reg = new LinkedHashMap<>();
        reg.put("registrationType", mapRegistrationMode(strVal(body, "registration_mode")));
        reg.put("initiatedChannel", firstNonBlank(
                strVal(body, "source_workflow"),
                strVal(body, "initiating_context"),
                "EXPERIENCE_VITO_WIZARD"));
        reg.put("firstName", strVal(body, "given_name", "givenName", "firstName"));
        reg.put("middleName", strVal(body, "middle_name", "middleName"));
        reg.put("lastName", strVal(body, "family_name", "familyName", "lastName"));
        reg.put("dateOfBirth", strVal(body, "date_of_birth", "dateOfBirth"));
        reg.put("sex", strVal(body, "sex", "gender"));
        reg.put("phone", strVal(body, "phone"));
        reg.put("email", strVal(body, "email"));
        reg.put("nationalIdReference", strVal(body, "national_id", "nationalId", "nationalIdReference"));
        reg.put("passportReference", strVal(body, "passport_reference", "passportReference"));
        reg.put("addressLine1", strVal(body, "address_line", "address_line1", "addressLine1"));
        reg.put("city", strVal(body, "city"));
        reg.put("preferredLanguage", strVal(body, "preferred_language", "preferredLanguage"));
        reg.put("maritalStatus", strVal(body, "marital_status", "maritalStatus"));
        reg.put("emergencyContactName", strVal(body, "emergency_contact_name", "emergencyContactName"));
        reg.put("emergencyContactPhone", strVal(body, "emergency_contact_phone", "emergencyContactPhone"));
        reg.put("issueProvisionalIdentifier", true);
        reg.put("metadata", metadata);

        String facilityId = strVal(body, "facility_id", "facilityId");
        if (facilityId != null && !facilityId.isBlank()) {
            try {
                reg.put("linkedFacilityId", Long.parseLong(facilityId));
            } catch (NumberFormatException ignored) {
                metadata.put("facilityId", facilityId);
                reg.put("metadata", metadata);
            }
        }

        String providerId = strVal(body, "provider_id", "providerId", "linkedProviderId");
        if (providerId != null && !providerId.isBlank()) {
            reg.put("linkedProviderId", providerId);
        }

        return reg;
    }

    private static String mapRegistrationMode(String registrationMode) {
        if (registrationMode == null || registrationMode.isBlank()) {
            return "FACILITY_REGISTRATION";
        }
        return switch (registrationMode.toUpperCase()) {
            case "SELF_INITIATED", "CITIZEN_SELF_SERVICE" -> "SELF_INITIATED";
            case "COMMUNITY_REGISTRATION", "OUTREACH_REGISTRATION" -> "COMMUNITY_REGISTRATION";
            case "VIRTUAL_REGISTRATION" -> "VIRTUAL_REGISTRATION";
            default -> "FACILITY_REGISTRATION";
        };
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, Object> mapIssuanceToPatient(
            JsonNode result, String given, String family, String dob, String sex,
            String nationalId, String phone) {
        String healthId = textOrNull(result, "healthId");
        if (healthId == null) {
            healthId = "pat-" + UUID.randomUUID().toString().substring(0, 8);
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("impiloHealthId", healthId);
        attrs.put("cpid", healthId);
        attrs.put("givenName", given);
        attrs.put("familyName", family);
        attrs.put("displayName", (given + " " + family).trim());
        attrs.put("dateOfBirth", dob != null ? dob : "");
        attrs.put("sex", sex != null ? sex : "unknown");
        attrs.put("nationalId", nationalId);
        attrs.put("phone", phone);
        attrs.put("status", textOrNull(result, "status"));
        attrs.put("age", dob != null && !dob.isBlank()
                ? LocalDate.now().getYear() - LocalDate.parse(dob).getYear()
                : null);
        return Map.of("id", healthId, "type", "patient", "attributes", attrs);
    }

    private static Map<String, Object> mapRegistrySummaryToPatient(JsonNode item) {
        String healthId = textOrNull(item, "healthId");
        if (healthId == null) {
            healthId = UUID.randomUUID().toString();
        }
        String impiloId = textOrNull(item, "impiloId");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("impiloHealthId", healthId);
        attrs.put("impiloId", impiloId);
        attrs.put("cpid", impiloId != null ? impiloId : healthId);
        attrs.put("displayName", textOrNull(item, "displayName"));
        attrs.put("givenName", "");
        attrs.put("familyName", "");
        attrs.put("dateOfBirth", "");
        attrs.put("sex", "unknown");
        attrs.put("lifecycleStatus", textOrNull(item, "lifecycleStatus"));
        attrs.put("verificationStatus", textOrNull(item, "verificationStatus"));
        attrs.put("status", textOrNull(item, "lifecycleStatus"));
        return Map.of("id", healthId, "type", "patient", "attributes", attrs);
    }

    private static Map<String, Object> mapClientProfileToPatient(JsonNode profile) {
        JsonNode master = profile.get("master");
        if (master == null || master.isNull()) {
            return Map.of("id", "unknown", "type", "patient", "attributes", Map.of());
        }
        String healthId = textOrNull(master, "healthId");
        String impiloId = textOrNull(master, "impiloId");
        String first = textOrNull(master, "firstName");
        String middle = textOrNull(master, "middleName");
        String last = textOrNull(master, "lastName");
        String dob = textOrNull(master, "dateOfBirth");
        String sex = textOrNull(master, "sex");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("impiloHealthId", healthId);
        attrs.put("impiloId", impiloId);
        attrs.put("cpid", impiloId != null ? impiloId : healthId);
        attrs.put("givenName", first != null ? first : "");
        attrs.put("middleName", middle);
        attrs.put("familyName", last != null ? last : "");
        attrs.put("displayName", buildDisplayName(first, middle, last));
        attrs.put("dateOfBirth", dob != null ? dob : "");
        attrs.put("sex", sex != null ? sex : "unknown");
        attrs.put("lifecycleStatus", textOrNull(master, "lifecycleStatus"));
        attrs.put("verificationStatus", textOrNull(master, "verificationStatus"));
        attrs.put("status", textOrNull(master, "lifecycleStatus"));
        putNestedPatientFields(attrs, master.get("contacts"), "phone", "email",
                "emergencyContactName", "emergencyContactPhone");
        putNestedPatientFields(attrs, master.get("address"), "addressLine1", "city");
        putNestedPatientFields(attrs, master.get("demographics"), "preferredLanguage", "maritalStatus");
        putPassportReferenceFromIdentifiers(attrs, profile);
        if (dob != null && !dob.isBlank()) {
            try {
                attrs.put("age", LocalDate.now().getYear() - LocalDate.parse(dob).getYear());
            } catch (Exception ignored) {
            }
        }
        return Map.of("id", healthId != null ? healthId : UUID.randomUUID().toString(), "type", "patient", "attributes", attrs);
    }

    private static String buildDisplayName(String first, String middle, String last) {
        StringBuilder sb = new StringBuilder();
        if (first != null && !first.isBlank()) {
            sb.append(first.trim());
        }
        if (middle != null && !middle.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(middle.trim());
        }
        if (last != null && !last.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(last.trim());
        }
        return sb.toString().trim();
    }

    private static void putNestedPatientFields(Map<String, Object> attrs, JsonNode node, String... keys) {
        if (node == null || node.isNull() || !node.isObject()) {
            return;
        }
        for (String key : keys) {
            String value = textOrNull(node, key);
            if (value != null) {
                attrs.put(key, value);
            }
        }
    }

    /**
     * Maps Vito client-registry {@code identifiers[]} onto patient attributes for GET read-back.
     */
    private static void putPassportReferenceFromIdentifiers(Map<String, Object> attrs, JsonNode profile) {
        if (profile == null || profile.isNull()) {
            return;
        }
        JsonNode identifiers = profile.get("identifiers");
        if (identifiers == null || identifiers.isNull() || !identifiers.isArray()) {
            return;
        }
        for (JsonNode identifier : identifiers) {
            if (identifier == null || identifier.isNull()) {
                continue;
            }
            if (!"PASSPORT_REFERENCE".equals(textOrNull(identifier, "identifierType"))) {
                continue;
            }
            String value = textOrNull(identifier, "identifierValue");
            if (value != null && !value.isBlank()) {
                attrs.put("passportReference", value.trim());
            }
            return;
        }
    }

    private static Map<String, Object> mapClientEntityToPatient(JsonNode entity) {
        String healthId = textOrNull(entity, "healthId");
        if (healthId == null) {
            healthId = textOrNull(entity, "id");
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("impiloHealthId", healthId);
        attrs.put("cpid", healthId);
        attrs.put("displayName", textOrNull(entity, "displayName"));
        attrs.put("givenName", textOrNull(entity, "givenName"));
        attrs.put("familyName", textOrNull(entity, "familyName"));
        attrs.put("dateOfBirth", textOrNull(entity, "dateOfBirth"));
        attrs.put("sex", textOrNull(entity, "sex"));
        attrs.put("status", textOrNull(entity, "status"));
        return Map.of("id", healthId != null ? healthId : UUID.randomUUID().toString(), "type", "patient", "attributes", attrs);
    }

    private static String textOrNull(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        return n.get(field).asText();
    }

    private static String strVal(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) {
                return v.toString();
            }
        }
        return null;
    }
}
