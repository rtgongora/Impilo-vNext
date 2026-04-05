package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

/**
 * Publishes FHIR R4 resources to HAPI FHIR (BUTANO) when clinical events
 * occur in the experience BFF. Follows the architecture rule: "No PII in SHR"
 * — only CPID (client patient ID) is used, never name/address/phone.
 *
 * <p>Resources published:
 * <ul>
 *   <li>Encounter — when an encounter is created/closed</li>
 *   <li>Condition — when a diagnosis is recorded</li>
 *   <li>MedicationRequest — when a prescription is created</li>
 *   <li>DiagnosticReport — when lab results are available</li>
 *   <li>Observation — when vitals are recorded</li>
 * </ul>
 */
@Component
public class FhirPublisher {

    private static final Logger log = LoggerFactory.getLogger(FhirPublisher.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String fhirBaseUrl;

    public FhirPublisher(
            ObjectMapper objectMapper,
            @Value("${impilo.services.fhir-base-url:http://localhost:8090/fhir}") String fhirBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.fhirBaseUrl = fhirBaseUrl;
    }

    /**
     * Publish an Encounter resource to BUTANO.
     */
    public void publishEncounter(String cpid, String encounterId, String encounterClass,
                                  String status, String facilityId, String tenantId) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Encounter");
        resource.put("id", encounterId);
        resource.put("status", mapEncounterStatus(status));
        resource.put("class", Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                "code", encounterClass != null ? encounterClass : "AMB"));
        resource.put("subject", Map.of("reference", "Patient/" + cpid));
        resource.put("serviceProvider", Map.of("reference", "Organization/" + facilityId));
        resource.put("meta", Map.of(
                "tag", List.of(Map.of(
                        "system", "http://impilo.gov.zw/fhir/tenant",
                        "code", tenantId))));

        publishResource("Encounter", encounterId, resource);
    }

    /**
     * Publish a Condition (diagnosis) resource.
     */
    public void publishCondition(String cpid, String conditionId, String icdCode,
                                  String displayName, String encounterId, String tenantId) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Condition");
        resource.put("id", conditionId);
        resource.put("clinicalStatus", Map.of("coding", List.of(Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/condition-clinical",
                "code", "active"))));
        resource.put("code", Map.of("coding", List.of(Map.of(
                "system", "http://hl7.org/fhir/sid/icd-11",
                "code", icdCode,
                "display", displayName))));
        resource.put("subject", Map.of("reference", "Patient/" + cpid));
        resource.put("encounter", Map.of("reference", "Encounter/" + encounterId));
        resource.put("recordedDate", LocalDate.now().toString());
        resource.put("meta", Map.of(
                "tag", List.of(Map.of(
                        "system", "http://impilo.gov.zw/fhir/tenant",
                        "code", tenantId))));

        publishResource("Condition", conditionId, resource);
    }

    /**
     * Publish a MedicationRequest resource.
     */
    public void publishMedicationRequest(String cpid, String prescriptionId, String medicationCode,
                                          String medicationName, String encounterId, String tenantId) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "MedicationRequest");
        resource.put("id", prescriptionId);
        resource.put("status", "active");
        resource.put("intent", "order");
        resource.put("medicationCodeableConcept", Map.of("coding", List.of(Map.of(
                "system", "http://impilo.gov.zw/fhir/medication",
                "code", medicationCode,
                "display", medicationName))));
        resource.put("subject", Map.of("reference", "Patient/" + cpid));
        resource.put("encounter", Map.of("reference", "Encounter/" + encounterId));
        resource.put("authoredOn", LocalDate.now().toString());
        resource.put("meta", Map.of(
                "tag", List.of(Map.of(
                        "system", "http://impilo.gov.zw/fhir/tenant",
                        "code", tenantId))));

        publishResource("MedicationRequest", prescriptionId, resource);
    }

    /**
     * Publish an Observation (vitals) resource.
     */
    public void publishObservation(String cpid, String observationId, String loincCode,
                                    String displayName, String value, String unit,
                                    String encounterId, String tenantId) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Observation");
        resource.put("id", observationId);
        resource.put("status", "final");
        resource.put("code", Map.of("coding", List.of(Map.of(
                "system", "http://loinc.org",
                "code", loincCode,
                "display", displayName))));
        resource.put("subject", Map.of("reference", "Patient/" + cpid));
        resource.put("encounter", Map.of("reference", "Encounter/" + encounterId));
        resource.put("valueQuantity", Map.of(
                "value", value,
                "unit", unit,
                "system", "http://unitsofmeasure.org"));
        resource.put("effectiveDateTime", java.time.OffsetDateTime.now().toString());
        resource.put("meta", Map.of(
                "tag", List.of(Map.of(
                        "system", "http://impilo.gov.zw/fhir/tenant",
                        "code", tenantId))));

        publishResource("Observation", observationId, resource);
    }

    /**
     * Publish a DiagnosticReport resource.
     */
    public void publishDiagnosticReport(String cpid, String reportId, String loincCode,
                                         String displayName, String conclusion,
                                         String encounterId, String tenantId) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "DiagnosticReport");
        resource.put("id", reportId);
        resource.put("status", "final");
        resource.put("code", Map.of("coding", List.of(Map.of(
                "system", "http://loinc.org",
                "code", loincCode,
                "display", displayName))));
        resource.put("subject", Map.of("reference", "Patient/" + cpid));
        resource.put("encounter", Map.of("reference", "Encounter/" + encounterId));
        resource.put("conclusion", conclusion);
        resource.put("effectiveDateTime", java.time.OffsetDateTime.now().toString());
        resource.put("meta", Map.of(
                "tag", List.of(Map.of(
                        "system", "http://impilo.gov.zw/fhir/tenant",
                        "code", tenantId))));

        publishResource("DiagnosticReport", reportId, resource);
    }

    private void publishResource(String resourceType, String id, Map<String, Object> resource) {
        try {
            String json = objectMapper.writeValueAsString(resource);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            String url = fhirBaseUrl + "/" + resourceType + "/" + id;

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.PUT, entity, String.class);

            log.info("FHIR {} published: id={}, status={}", resourceType, id, response.getStatusCode());
        } catch (Exception e) {
            log.warn("Failed to publish FHIR {} id={}: {}", resourceType, id, e.getMessage());
        }
    }

    private String mapEncounterStatus(String bffStatus) {
        return switch (bffStatus != null ? bffStatus.toUpperCase() : "IN_PROGRESS") {
            case "IN_PROGRESS", "ACTIVE" -> "in-progress";
            case "COMPLETED", "CLOSED" -> "finished";
            case "CANCELLED" -> "cancelled";
            default -> "in-progress";
        };
    }
}
