package zw.gov.mohcc.impilo.fhirgateway.events;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.fhirgateway.config.FhirGatewayProperties;

import java.util.Map;

/**
 * Consumes experience-bff domain events from Kafka and persists them as
 * FHIR R4 resources in HAPI FHIR (butano-fhir) via the FHIR gateway backend.
 *
 * <p>Topics consumed:</p>
 * <ul>
 *   <li>{@code experience.allergy}  → FHIR {@code AllergyIntolerance}</li>
 *   <li>{@code experience.encounter} → FHIR {@code Encounter}</li>
 *   <li>{@code experience.vitals}   → FHIR {@code Observation} (vital signs bundle)</li>
 * </ul>
 *
 * <p>Each message is an EventEnvelope published by {@code ExperienceBffOutboxPublisher}
 * with {@code source_service = "experience-bff"} and a {@code payload} field containing
 * the domain data. The gateway extracts the payload and builds a minimal FHIR R4 resource
 * then conditionally upserts it via {@code PUT .../fhir/{Type}/{id}} so the operation
 * is idempotent on replay.</p>
 */
@Component
public class ExperienceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExperienceEventConsumer.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RestTemplate restTemplate;
    private final FhirGatewayProperties properties;
    private final ObjectMapper objectMapper;

    public ExperienceEventConsumer(RestTemplate restTemplate,
                                   FhirGatewayProperties properties,
                                   ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"experience.allergy"},
            groupId = "fhir-gateway",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAllergyEvent(String message) {
        Map<String, Object> payload = extractPayload(message);
        if (payload == null) return;

        String allergyId = str(payload, "allergy_id");
        String patientId = str(payload, "patient_id");
        String allergen  = str(payload, "allergen");
        String severity  = str(payload, "severity");
        String reaction  = str(payload, "reaction");
        String status    = str(payload, "status");
        String tenantId  = str(payload, "tenant_id");

        if (allergyId == null || patientId == null || allergen == null) {
            log.warn("ExperienceEventConsumer: allergy missing required fields, keys={}", payload.keySet());
            return;
        }

        String clinicalStatusCode = "INACTIVE".equals(status) ? "inactive" : "active";
        String criticality = "HIGH".equals(severity) || "SEVERE".equals(severity) ? "high" : "low";

        String fhirJson = """
                {
                  "resourceType": "AllergyIntolerance",
                  "id": "%s",
                  "meta": {
                    "tag": [{"system": "http://impilo.mohcc.zw/tenant", "code": "%s"}]
                  },
                  "identifier": [{"system": "http://impilo.mohcc.zw/allergy", "value": "%s"}],
                  "clinicalStatus": {
                    "coding": [{"system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", "code": "%s"}]
                  },
                  "verificationStatus": {
                    "coding": [{"system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification", "code": "confirmed"}]
                  },
                  "criticality": "%s",
                  "code": {"text": "%s"},
                  "patient": {"reference": "Patient/%s"},
                  "reaction": [{"manifestation": [{"text": "%s"}]}]
                }
                """.formatted(
                allergyId, tenantId != null ? tenantId : "unknown",
                allergyId, clinicalStatusCode, criticality,
                escape(allergen), patientId,
                reaction != null ? escape(reaction) : allergen
        );

        putFhirResource("AllergyIntolerance", allergyId, fhirJson, tenantId);
    }

    @KafkaListener(
            topics = {"experience.encounter"},
            groupId = "fhir-gateway",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEncounterEvent(String message) {
        Map<String, Object> payload = extractPayload(message);
        if (payload == null) return;

        String encounterId    = str(payload, "encounter_id");
        String patientId      = str(payload, "patient_id");
        String facilityId     = str(payload, "facility_id");
        String encounterType  = str(payload, "encounter_type");
        String status         = str(payload, "status");
        String tenantId       = str(payload, "tenant_id");

        if (encounterId == null || patientId == null) {
            log.warn("ExperienceEventConsumer: encounter missing required fields, keys={}", payload.keySet());
            return;
        }

        String fhirStatus = mapEncounterStatus(status);
        String classCode  = mapEncounterClass(encounterType);

        String fhirJson = """
                {
                  "resourceType": "Encounter",
                  "id": "%s",
                  "meta": {
                    "tag": [{"system": "http://impilo.mohcc.zw/tenant", "code": "%s"}]
                  },
                  "identifier": [{"system": "http://impilo.mohcc.zw/encounter", "value": "%s"}],
                  "status": "%s",
                  "class": {
                    "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                    "code": "%s"
                  },
                  "subject": {"reference": "Patient/%s"},
                  "serviceProvider": {"reference": "Organization/%s"}
                }
                """.formatted(
                encounterId,
                tenantId != null ? tenantId : "unknown",
                encounterId, fhirStatus, classCode,
                patientId,
                facilityId != null ? facilityId : "unknown"
        );

        putFhirResource("Encounter", encounterId, fhirJson, tenantId);
    }

    @KafkaListener(
            topics = {"experience.vitals"},
            groupId = "fhir-gateway",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onVitalsEvent(String message) {
        Map<String, Object> payload = extractPayload(message);
        if (payload == null) return;

        String vitalsId    = str(payload, "vitals_id");
        String patientId   = str(payload, "patient_id");
        String encounterId = str(payload, "encounter_id");
        String tenantId    = str(payload, "tenant_id");
        String recordedAt  = str(payload, "recorded_at");

        if (vitalsId == null || patientId == null) {
            log.warn("ExperienceEventConsumer: vitals missing required fields, keys={}", payload.keySet());
            return;
        }

        String effectiveTime = recordedAt != null ? recordedAt : java.time.OffsetDateTime.now().toString();
        String encounterRef  = (encounterId != null && !encounterId.isBlank())
                ? "\"encounter\": {\"reference\": \"Encounter/" + encounterId + "\"}," : "";

        StringBuilder components = new StringBuilder();
        appendObsComponent(components, "8480-6",  "Systolic BP",      payload, "systolic",          "mm[Hg]");
        appendObsComponent(components, "8462-4",  "Diastolic BP",     payload, "diastolic",         "mm[Hg]");
        appendObsComponent(components, "8867-4",  "Heart rate",       payload, "heart_rate",        "/min");
        appendObsComponent(components, "8310-5",  "Body temperature", payload, "temperature",       "Cel");
        appendObsComponent(components, "9279-1",  "Resp rate",        payload, "respiratory_rate",  "/min");
        appendObsComponent(components, "59408-5", "O2 saturation",    payload, "oxygen_saturation", "%");
        appendObsComponent(components, "29463-7", "Body weight",      payload, "weight",            "kg");
        appendObsComponent(components, "8302-2",  "Body height",      payload, "height",            "cm");
        appendObsComponent(components, "39156-5", "BMI",              payload, "bmi",               "kg/m2");
        appendObsComponent(components, "72514-3", "Pain score",       payload, "pain_score",        "{score}");

        if (components.length() > 1) {
            components.deleteCharAt(components.length() - 1);
        }

        String fhirJson = """
                {
                  "resourceType": "Observation",
                  "id": "%s",
                  "meta": {
                    "tag": [{"system": "http://impilo.mohcc.zw/tenant", "code": "%s"}]
                  },
                  "identifier": [{"system": "http://impilo.mohcc.zw/vitals", "value": "%s"}],
                  "status": "final",
                  "category": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/observation-category", "code": "vital-signs"}]}],
                  "code": {"coding": [{"system": "http://loinc.org", "code": "85353-1"}], "text": "Vital signs panel"},
                  "subject": {"reference": "Patient/%s"},
                  %s
                  "effectiveDateTime": "%s",
                  "component": [%s]
                }
                """.formatted(
                vitalsId,
                tenantId != null ? tenantId : "unknown",
                vitalsId, patientId,
                encounterRef,
                effectiveTime,
                components
        );

        putFhirResource("Observation", vitalsId, fhirJson, tenantId);
    }

    private void appendObsComponent(StringBuilder sb, String loincCode, String display,
                                    Map<String, Object> payload, String key, String unit) {
        Object val = payload.get(key);
        if (val == null) return;
        try {
            double v = Double.parseDouble(val.toString());
            sb.append("""
                    {"code":{"coding":[{"system":"http://loinc.org","code":"%s","display":"%s"}]},
                     "valueQuantity":{"value":%s,"unit":"%s","system":"http://unitsofmeasure.org","code":"%s"}},
                    """.formatted(loincCode, display, v, unit, unit));
        } catch (NumberFormatException ignored) {}
    }

    private void putFhirResource(String resourceType, String id, String fhirJson, String tenantId) {
        String url = properties.getBackendUrl() + "/fhir/" + resourceType + "/" + id;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/fhir+json"));
        if (tenantId != null) {
            headers.set("X-Tenant-ID", tenantId);
        }
        HttpEntity<String> entity = new HttpEntity<>(fhirJson, headers);
        try {
            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            log.info("ExperienceEventConsumer: upserted FHIR {} id={}", resourceType, id);
        } catch (HttpStatusCodeException ex) {
            log.error("ExperienceEventConsumer: FHIR {} PUT failed status={} body={}",
                    resourceType, ex.getStatusCode(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("ExperienceEventConsumer: FHIR {} PUT error: {}", resourceType, ex.getMessage(), ex);
        }
    }

    private Map<String, Object> extractPayload(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            boolean isEnvelope = root.has("event_id") || root.has("eventId");
            if (isEnvelope) {
                JsonNode payloadNode = root.path("payload");
                if (payloadNode.isMissingNode() || payloadNode.isNull()) {
                    log.warn("ExperienceEventConsumer: envelope has no payload, skipping");
                    return null;
                }
                Map<String, Object> payload = objectMapper.convertValue(payloadNode, MAP_TYPE);
                String tenantId = root.path("tenant_id").asText(null);
                if (tenantId != null) payload.put("tenant_id", tenantId);
                return payload;
            }
            return objectMapper.convertValue(root, MAP_TYPE);
        } catch (Exception e) {
            log.error("ExperienceEventConsumer: failed to parse message: {}", e.getMessage());
            return null;
        }
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private String escape(String s) {
        return s != null ? s.replace("\\", "\\\\").replace("\"", "\\\"") : "";
    }

    private String mapEncounterStatus(String status) {
        if (status == null) return "in-progress";
        return switch (status.toUpperCase()) {
            case "IN_PROGRESS" -> "in-progress";
            case "COMPLETED"   -> "finished";
            case "CANCELLED"   -> "cancelled";
            default            -> "in-progress";
        };
    }

    private String mapEncounterClass(String encounterType) {
        if (encounterType == null) return "AMB";
        return switch (encounterType.toUpperCase()) {
            case "INPATIENT"   -> "IMP";
            case "EMERGENCY"   -> "EMER";
            case "HOME_VISIT"  -> "HH";
            default            -> "AMB";
        };
    }
}
