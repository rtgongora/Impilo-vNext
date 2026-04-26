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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Patient / client directory for walk-in and shell flows — delegates to VITO
 * client registry and identity issuance, with seeded fallback when downstream
 * is unavailable.
 */
@RestController
@RequestMapping("/internal/v1/patients")
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);
    private final VitoServiceClient vitoClient;
    private static final List<Map<String, Object>> PATIENTS = new CopyOnWriteArrayList<>(buildSeeded());

    public PatientController(VitoServiceClient vitoClient) {
        this.vitoClient = vitoClient;
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
            log.info("VITO unavailable — creating patient locally: {}", e.getMessage());
        }

        String id = "pat-" + UUID.randomUUID().toString().substring(0, 8);
        String cpid = "CPID-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> patient = patient(id, cpid,
                givenName, familyName != null ? familyName : "",
                dob, sex != null ? sex : "unknown", nationalId, phone);
        patient = withRegistrationOverlay(patient, patientData, false);
        PATIENTS.add(patient);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", patient);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(201).body(response);
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
            if (!mapped.isEmpty()) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", mapped);
                response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                        "page", Map.of("number", page, "size", size, "total_elements", total)));
                return ResponseEntity.ok(response);
            }
            log.debug("VITO returned no client rows — using local seeded directory");
        } catch (Exception e) {
            log.warn("VITO client registry list failed, using seed: {}", e.getMessage());
        }

        List<Map<String, Object>> filtered = PATIENTS;
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            filtered = filtered.stream().filter(p -> {
                Map<?, ?> a = (Map<?, ?>) p.get("attributes");
                String name = a.get("givenName") + " " + a.get("familyName");
                String cpid = (String) a.get("cpid");
                String nid = a.get("nationalId") != null ? (String) a.get("nationalId") : "";
                return name.toLowerCase().contains(q) || cpid.toLowerCase().contains(q) || nid.toLowerCase().contains(q);
            }).collect(Collectors.toList());
        }

        int start = page * size;
        int end = Math.min(start + size, filtered.size());
        List<Map<String, Object>> paged = start < filtered.size() ? filtered.subList(start, end) : List.of();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", paged);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", size, "total_elements", filtered.size())));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPatient(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        try {
            JsonNode profile = vitoClient.getClientRegistryProfile(id);
            if (profile != null) {
                Map<String, Object> patient = mapClientProfileToPatient(profile);
                return ResponseEntity.ok(Map.of(
                        "data", patient,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception e) {
            log.debug("VITO client profile miss for id={}: {}", id, e.getMessage());
        }

        try {
            JsonNode entity = vitoClient.getPatient(id);
            if (entity != null) {
                Map<String, Object> patient = mapClientEntityToPatient(entity);
                return ResponseEntity.ok(Map.of(
                        "data", patient,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception ignored) {
        }

        return PATIENTS.stream()
                .filter(p -> p.get("id").equals(id))
                .findFirst()
                .map(p -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("data", p);
                    body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
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
        copyIfPresent(attrs, request, "coverage");
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
        String last = textOrNull(master, "lastName");
        String dob = textOrNull(master, "dateOfBirth");
        String sex = textOrNull(master, "sex");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("impiloHealthId", healthId);
        attrs.put("impiloId", impiloId);
        attrs.put("cpid", impiloId != null ? impiloId : healthId);
        attrs.put("givenName", first != null ? first : "");
        attrs.put("familyName", last != null ? last : "");
        attrs.put("displayName", ((first != null ? first : "") + " " + (last != null ? last : "")).trim());
        attrs.put("dateOfBirth", dob != null ? dob : "");
        attrs.put("sex", sex != null ? sex : "unknown");
        attrs.put("lifecycleStatus", textOrNull(master, "lifecycleStatus"));
        attrs.put("verificationStatus", textOrNull(master, "verificationStatus"));
        attrs.put("status", textOrNull(master, "lifecycleStatus"));
        if (dob != null && !dob.isBlank()) {
            try {
                attrs.put("age", LocalDate.now().getYear() - LocalDate.parse(dob).getYear());
            } catch (Exception ignored) {
            }
        }
        return Map.of("id", healthId != null ? healthId : UUID.randomUUID().toString(), "type", "patient", "attributes", attrs);
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

    private static List<Map<String, Object>> buildSeeded() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(patient("pat-001", "CPID-ZW-00001", "Tatenda", "Moyo", "1990-03-15", "male", "63-123456-A-78", "+263771234567"));
        list.add(patient("pat-002", "CPID-ZW-00002", "Rumbidzai", "Chienda", "1985-07-22", "female", "63-234567-B-89", "+263772345678"));
        list.add(patient("pat-003", "CPID-ZW-00003", "Takudzwa", "Ndlovu", "2001-11-03", "male", "63-345678-C-90", "+263773456789"));
        list.add(patient("pat-004", "CPID-ZW-00004", "Chiedza", "Mapfumo", "1978-01-28", "female", "63-456789-D-01", "+263774567890"));
        list.add(patient("pat-005", "CPID-ZW-00005", "Tendai", "Zenda", "1995-09-10", "male", null, "+263775678901"));
        list.add(patient("pat-006", "CPID-ZW-00006", "Nyasha", "Chirandu", "2010-05-20", "female", null, "+263776789012"));
        list.add(patient("pat-007", "CPID-ZW-00007", "Farai", "Mutasa", "1968-12-01", "male", "63-567890-E-12", "+263777890123"));
        list.add(patient("pat-008", "CPID-ZW-00008", "Tsitsi", "Gumbo", "2003-08-14", "female", null, null));
        list.add(patient("pat-009", "CPID-ZW-00009", "Simba", "Makoni", "1992-04-07", "male", "63-678901-F-23", "+263778901234"));
        list.add(patient("pat-010", "CPID-ZW-00010", "Rudo", "Sibanda", "1988-06-30", "female", "63-789012-G-34", "+263779012345"));
        return list;
    }

    private static Map<String, Object> patient(String id, String cpid,
            String given, String family, String dob, String sex,
            String nationalId, String phone) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("cpid", cpid);
        attrs.put("givenName", given);
        attrs.put("familyName", family);
        attrs.put("displayName", given + " " + family);
        attrs.put("dateOfBirth", dob);
        attrs.put("sex", sex);
        attrs.put("nationalId", nationalId);
        attrs.put("phone", phone);
        attrs.put("status", "ACTIVE");
        attrs.put("age", dob != null ? LocalDate.now().getYear() - LocalDate.parse(dob).getYear() : null);
        return Map.of("id", id, "type", "patient", "attributes", attrs);
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
