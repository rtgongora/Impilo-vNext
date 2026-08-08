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
 * Publishes FHIR R4 resources to BUTANO (the SHR) when clinical events occur in the experience
 * BFF. Follows the architecture rule: "No PII in SHR" — only CPID (client patient ID) is used,
 * never name/address/phone.
 *
 * <p>Resources published:
 * <ul>
 *   <li>Encounter — when an encounter is created/closed</li>
 *   <li>Condition — when a diagnosis is recorded</li>
 *   <li>MedicationRequest — when a prescription is created</li>
 *   <li>DiagnosticReport — when lab results are available</li>
 *   <li>Observation — when vitals are recorded</li>
 * </ul>
 *
 * <p><b>Nothing injects this class.</b> It is a {@code @Component}, so Spring builds it, but a
 * search for the type outside this file returns nothing and
 * {@code ServiceClientConfig.ServiceEndpoints.fhirBaseUrl()} is never read. It is also write-only
 * — six {@code publish*} methods, all HTTP PUT, no read path. So {@code FHIR_BASE_URL} is inert
 * today and repointing it changes nothing observable; it is repointed anyway because the value it
 * carried named the ungoverned stock HAPI server, and the cost of that being wrong is only paid
 * at the moment someone wires this up.</p>
 *
 * <p>Two things to know before wiring it up. It bypasses fhir-gateway-service, where the consent
 * PEP lives, so these writes are unconsented and unaudited by construction — the governed seam is
 * {@code FhirGatewayServiceClient.forward}, which
 * {@code TeleconsultController.writeTeleconsultSummaryToFhir} uses. And its subject references are
 * {@code "Patient/" + cpid}, which does not resolve in BUTANO: a CPID is not a FHIR logical id,
 * and BUTANO enforces referential integrity on write. Every publish here would be rejected with
 * HAPI-1094 until those become match URLs, the way the two live callers now do it.</p>
 */
@Component
public class FhirPublisher {

    private static final Logger log = LoggerFactory.getLogger(FhirPublisher.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String fhirBaseUrl;

    public FhirPublisher(
            RestTemplate serviceRestTemplate,
            ObjectMapper objectMapper,
            @Value("${impilo.services.fhir-base-url:http://localhost:8090/fhir}") String fhirBaseUrl) {
        this.restTemplate = serviceRestTemplate;
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

    /**
     * Publish an ImagingStudy resource from a PACS study event.
     * Links the DICOM study to the patient (via CPID) and the originating encounter.
     */
    public void publishImagingStudy(String cpid, String studyId, String studyInstanceUid,
                                     String modality, String description, int numberOfSeries,
                                     int numberOfInstances, String encounterId, String tenantId) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "ImagingStudy");
        resource.put("id", studyId);
        resource.put("status", "available");
        resource.put("subject", Map.of("reference", "Patient/" + cpid));
        if (encounterId != null && !encounterId.isEmpty()) {
            resource.put("encounter", Map.of("reference", "Encounter/" + encounterId));
        }
        resource.put("started", java.time.OffsetDateTime.now().toString());
        resource.put("numberOfSeries", numberOfSeries);
        resource.put("numberOfInstances", numberOfInstances);
        resource.put("description", description != null ? description : "");

        // Modality coding
        resource.put("modality", List.of(Map.of(
                "system", "http://dicom.nema.org/resources/ontology/DCM",
                "code", modality != null ? modality : "OT")));

        // Series with endpoint reference to our PACS proxy
        resource.put("series", List.of(Map.of(
                "uid", studyInstanceUid,
                "modality", Map.of(
                        "system", "http://dicom.nema.org/resources/ontology/DCM",
                        "code", modality != null ? modality : "OT"),
                "numberOfInstances", numberOfInstances)));

        // Endpoint reference for WADO-RS retrieval via BFF proxy
        resource.put("endpoint", List.of(Map.of(
                "reference", "Endpoint/impilo-pacs-wado-rs")));

        resource.put("meta", Map.of(
                "tag", List.of(Map.of(
                        "system", "http://impilo.gov.zw/fhir/tenant",
                        "code", tenantId))));

        publishResource("ImagingStudy", studyId, resource);
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
