package zw.gov.mohcc.impilo.experience.controller;

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
 * Patient registry proxy — delegates to VITO when available, falls back to
 * seeded patients so the walk-in registration flow always has data.
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
            @RequestBody Map<String, Object> body) {

        // Accept both camelCase and snake_case
        String givenName = strVal(body, "given_name", "givenName", "firstName");
        String familyName = strVal(body, "family_name", "familyName", "lastName");
        String displayName = strVal(body, "displayName", "display_name");
        String dob = strVal(body, "date_of_birth", "dateOfBirth");
        String sex = strVal(body, "sex", "gender");
        String nationalId = strVal(body, "national_id", "nationalId");
        String phone = strVal(body, "phone");
        String facilityId = strVal(body, "facility_id", "facilityId");

        // Parse displayName into given/family if individual names not provided
        if ((givenName == null || givenName.isBlank()) && displayName != null) {
            String[] parts = displayName.trim().split("\\s+", 2);
            givenName = parts[0];
            familyName = parts.length > 1 ? parts[1] : "";
        }
        if (givenName == null || givenName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "Patient name is required")));
        }

        // Try VITO first
        try {
            Map<String, Object> patientData = new LinkedHashMap<>();
            patientData.put("given_name", givenName);
            patientData.put("family_name", familyName);
            patientData.put("date_of_birth", dob);
            patientData.put("sex", sex);
            patientData.put("national_id", nationalId);
            patientData.put("phone", phone);
            patientData.put("facility_id", facilityId);
            patientData.put("tenant_id", tenantId);

            var result = vitoClient.registerPatient(patientData);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", result);
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            log.info("VITO unavailable — creating patient locally: {}", e.getMessage());
        }

        // Fallback
        String id = "pat-" + UUID.randomUUID().toString().substring(0, 8);
        String cpid = "CPID-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> patient = patient(id, cpid,
                givenName, familyName != null ? familyName : "",
                dob, sex != null ? sex : "unknown", nationalId, phone);
        PATIENTS.add(patient);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", patient);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listPatients(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {

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

        return PATIENTS.stream()
                .filter(p -> p.get("id").equals(id))
                .findFirst()
                .map(p -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("data", p);
                    r.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                    return ResponseEntity.ok(r);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Seeded patients ──────────────────────────────────────────────

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
            if (v != null) return v.toString();
        }
        return null;
    }
}
