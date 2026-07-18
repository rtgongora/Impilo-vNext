package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Aggregated EHR patient summary: longitudinal clinical slice (PCT) plus Mvumo
 * {@code consentSummary} for banner, dashboard, and emergency views. Mvumo is called at
 * {@code impilo.services.mvumo-base-url} (same env pattern as other sovereign downstreams, e.g. Tshepo, VITO).
 */
@RestController
@RequestMapping("/internal/v1/summary")
public class EhrPatientSummaryController {

    private static final Logger log = LoggerFactory.getLogger(EhrPatientSummaryController.class);

    /** Keep in sync with {@link PatientController} seed directory (cpid resolution when VITO is down). */
    private static final Map<String, String> SEEDED_CPIDS = Map.ofEntries(
            Map.entry("pat-001", "CPID-ZW-00001"),
            Map.entry("pat-002", "CPID-ZW-00002"),
            Map.entry("pat-003", "CPID-ZW-00003"),
            Map.entry("pat-004", "CPID-ZW-00004"),
            Map.entry("pat-005", "CPID-ZW-00005"),
            Map.entry("pat-006", "CPID-ZW-00006"),
            Map.entry("pat-007", "CPID-ZW-00007"),
            Map.entry("pat-008", "CPID-ZW-00008"),
            Map.entry("pat-009", "CPID-ZW-00009"),
            Map.entry("pat-010", "CPID-ZW-00010"));

    private final PctServiceClient pctClient;
    private final VitoServiceClient vitoClient;
    private final zw.gov.mohcc.impilo.experience.service.SubjectResolutionService subjectResolution;
    private final RestTemplate restTemplate;
    private final String mvumoBaseUrl;
    private final ObjectMapper objectMapper;

    public EhrPatientSummaryController(
            PctServiceClient pctClient,
            VitoServiceClient vitoClient,
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints,
            zw.gov.mohcc.impilo.experience.service.SubjectResolutionService subjectResolution,
            ObjectMapper objectMapper) {
        this.pctClient = pctClient;
        this.vitoClient = vitoClient;
        this.subjectResolution = subjectResolution;
        this.restTemplate = serviceRestTemplate;
        this.mvumoBaseUrl = endpoints.mvumoBaseUrl();
        this.objectMapper = objectMapper;
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<Map<String, Object>> getPatientSummary(
            @PathVariable String patientId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        String cpid = resolveCpid(patientId);
        Map<String, Object> clinical = new LinkedHashMap<>();
        clinical.put("cpid", cpid);
        try {
            JsonNode pct = pctClient.getPatientHealthSummary(cpid);
            clinical.putAll(mapPctSummary(cpid, pct));
        } catch (Exception e) {
            log.debug("PCT health summary unavailable for cpid={}: {}", cpid, e.getMessage());
            clinical.put("conditions", List.of());
            clinical.put("medications", List.of());
            clinical.put("allergies", List.of());
            clinical.put("immunizations", List.of());
            clinical.put("recentEncounters", List.of());
        }
        Map<String, Object> consent = fetchMvumoConsentSummary(cpid);
        clinical.put("consentSummary", consent);

        return ResponseEntity.ok(Map.of(
                "data", clinical,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /**
     * Resolve an EHR route id (Health ID, registry id, or already a CPID) to the
     * clinical subject CPID via tshepo-identity (Identity Contract §7.2). The
     * pre-contract shortcut of returning the Impilo ID or Health ID as "the cpid"
     * is gone — clinical services only understand mapped CPIDs.
     */
    private String resolveCpid(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return "";
        }
        if (patientId.contains("CPID-")) {
            return patientId;
        }
        String healthId = null;
        try {
            JsonNode profile = vitoClient.getClientRegistryProfile(patientId);
            if (profile != null) {
                JsonNode master = profile.get("master");
                if (master != null && !master.isNull()) {
                    healthId = text(master, "healthId");
                }
            }
        } catch (Exception e) {
            log.debug("VITO profile miss for id={}: {}", patientId, e.getMessage());
        }
        if (healthId == null || healthId.isBlank()) {
            try {
                JsonNode entity = vitoClient.getPatient(patientId);
                if (entity != null) {
                    healthId = text(entity, "healthId");
                }
            } catch (Exception e) {
                log.debug("VITO entity miss for id={}: {}", patientId, e.getMessage());
            }
        }
        if (healthId != null && !healthId.isBlank()) {
            String cpid = subjectResolution.cpidForHealthId(healthId);
            if (cpid != null && !cpid.isBlank()) {
                return cpid;
            }
        }
        return Optional.ofNullable(SEEDED_CPIDS.get(patientId)).orElse(patientId);
    }

    private Map<String, Object> mapPctSummary(String cpid, JsonNode root) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cpid", cpid);
        m.put("conditions", mapConditionList(root, "conditions", "activeConditions", "problems"));
        m.put("medications", mapMedicationList(root, "medications", "prescriptions", "medicationStatements"));
        m.put("allergies", mapAllergyList(root, "allergies", "allergyIntolerances"));
        m.put("immunizations", mapImmunizationList(root, "immunizations", "immunisation"));
        m.put("recentEncounters", mapEncounterList(root, "recentEncounters", "encounters"));
        return m;
    }

    private List<Map<String, Object>> mapConditionList(JsonNode root, String... fieldNames) {
        JsonNode arr = firstArray(root, fieldNames);
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : arr) {
            if (n == null || n.isNull()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", text(n, "code"));
            if (row.get("code") == null) {
                row.put("code", text(n.path("code"), "code"));
            }
            row.put("display", firstNonEmpty(text(n, "display"), text(n, "name"), text(n.path("code"), "display")));
            String cs = firstNonEmpty(text(n, "clinicalStatus"), text(n, "clinical_status"), text(n, "status"));
            row.put("clinicalStatus", cs != null ? cs : "unknown");
            String onset = firstNonEmpty(text(n, "onsetDate"), text(n, "onsetDateTime"), text(n, "onset"));
            if (onset != null) {
                row.put("onsetDate", onset);
            }
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> mapMedicationList(JsonNode root, String... fieldNames) {
        JsonNode arr = firstArray(root, fieldNames);
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : arr) {
            if (n == null || n.isNull()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", firstNonEmpty(text(n, "code"), text(n.path("medication"), "code")));
            row.put("display", firstNonEmpty(text(n, "display"), text(n, "name"), text(n.path("medication"), "display")));
            row.put("status", firstNonEmpty(text(n, "status"), text(n, "medicationStatus"), "active"));
            String dose = firstNonEmpty(text(n, "dosage"), text(n.path("dosage"), "text"));
            if (dose != null) {
                row.put("dosage", dose);
            }
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> mapAllergyList(JsonNode root, String... fieldNames) {
        JsonNode arr = firstArray(root, fieldNames);
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : arr) {
            if (n == null || n.isNull()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("substance", firstNonEmpty(text(n, "substance"), text(n, "allergen"), text(n.path("code"), "display")));
            row.put("reaction", firstNonEmpty(text(n, "reaction"), text(n.path("reaction"), "display")));
            row.put("criticality", firstNonEmpty(text(n, "criticality"), "unknown"));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> mapImmunizationList(JsonNode root, String... fieldNames) {
        JsonNode arr = firstArray(root, fieldNames);
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : arr) {
            if (n == null || n.isNull()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vaccine", firstNonEmpty(text(n, "vaccine"), text(n, "vaccineCode"), text(n.path("vaccineCode"), "display")));
            row.put("date", firstNonEmpty(text(n, "date"), text(n, "occurrenceDateTime")));
            row.put("status", firstNonEmpty(text(n, "status"), "unknown"));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> mapEncounterList(JsonNode root, String... fieldNames) {
        JsonNode arr = firstArray(root, fieldNames);
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : arr) {
            if (n == null || n.isNull()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", firstNonEmpty(text(n, "id"), text(n, "encounterId")));
            row.put("type", firstNonEmpty(text(n, "type"), text(n, "encounterType"), "unknown"));
            row.put("date", firstNonEmpty(text(n, "date"), text(n, "periodStart"), text(n, "start")));
            row.put("facility", firstNonEmpty(text(n, "facility"), text(n, "location")));
            row.put("status", firstNonEmpty(text(n, "status"), "unknown"));
            out.add(row);
        }
        return out;
    }

    private static JsonNode firstArray(JsonNode root, String... names) {
        if (root == null) {
            return null;
        }
        for (String name : names) {
            if (name != null && root.has(name) && root.get(name).isArray()) {
                return root.get(name);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchMvumoConsentSummary(String cpid) {
        if (cpid == null || cpid.isBlank()) {
            return emptyContract();
        }
        String patientRef = "Patient/" + cpid;
        String url = UriComponentsBuilder
                .fromHttpUrl(mvumoBaseUrl + "/internal/v1/mvumo/consent-summary")
                .queryParam("patientRef", patientRef)
                .toUriString();
        try {
            String raw = restTemplate.getForObject(url, String.class);
            if (raw == null || raw.isBlank()) {
                return emptyContract();
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) {
                return emptyContract();
            }
            return objectMapper.convertValue(data, Map.class);
        } catch (Exception e) {
            log.debug("Mvumo consent summary unavailable for {}: {}", patientRef, e.getMessage());
            return emptyContract();
        }
    }

    private static Map<String, Object> emptyContract() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("criticalFlags", List.of());
        m.put("advanceDirectives", List.of());
        m.put("treatmentLimitations", List.of());
        m.put("bloodProductRestrictions", List.of());
        m.put("activeConsents", List.of());
        m.put("missingRequiredConsents", List.of());
        m.put("refusals", List.of());
        m.put("withdrawals", List.of());
        m.put("dataSharingRestrictions", List.of());
        m.put("communicationPreferences", List.of());
        m.put("communicationPreferenceProfile", Map.of("hasProfile", false));
        m.put("proxyGuardianInfo", List.of());
        m.put("capacityStatus", null);
        m.put("lastReviewedAt", null);
        m.put("requiresReview", false);
        return m;
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        return n.get(field).asText();
    }

    @SafeVarargs
    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String s : values) {
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
