package zw.gov.mohcc.impilo.oros.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Integration service for the BUTANO Shared Health Record.
 *
 * <p>Creates FHIR resources (ServiceRequest, DiagnosticReport, DocumentReference)
 * in BUTANO when orders are placed and results are captured. Uses CPID-only
 * identifiers to comply with the PII-free SHR policy.</p>
 *
 * <p>All external calls degrade gracefully: if BUTANO is unavailable, the
 * failure is logged and a null reference is returned so that the calling
 * workflow can continue without blocking the order lifecycle.</p>
 */
@Service
public class ButanoIntegration {

    private static final Logger log = LoggerFactory.getLogger(ButanoIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ButanoIntegration(RestTemplate restTemplate,
                             @Value("${oros.integration.butano.base-url:http://localhost:8090}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Create a FHIR ServiceRequest in BUTANO for the given order.
     *
     * @param order the order entity to write back
     * @return the BUTANO reference (FHIR resource ID), or null if BUTANO is unavailable
     */
    @SuppressWarnings("unchecked")
    public String createServiceRequest(OrderEntity order) {
        try {
            String url = baseUrl + "/fhir/ServiceRequest";

            Map<String, Object> fhirResource = new HashMap<>();
            fhirResource.put("resourceType", "ServiceRequest");
            fhirResource.put("status", "active");
            fhirResource.put("intent", "order");

            // Subject is CPID-only (no PII in SHR)
            Map<String, String> subject = Map.of(
                    "reference", "Patient/" + order.getPatientCpid(),
                    "display", order.getPatientCpid()
            );
            fhirResource.put("subject", subject);

            // Order code from ZIBO
            if (order.getZiboOrderCode() != null) {
                Map<String, Object> code = Map.of(
                        "coding", java.util.List.of(Map.of(
                                "system", "urn:zibo:order-codes",
                                "code", order.getZiboOrderCode()
                        ))
                );
                fhirResource.put("code", code);
            }

            // Category from order type
            fhirResource.put("category", java.util.List.of(Map.of(
                    "coding", java.util.List.of(Map.of(
                            "system", "urn:oros:order-type",
                            "code", order.getOrderType().name()
                    ))
            )));

            // Priority mapping
            fhirResource.put("priority", order.getPriority().name().toLowerCase());

            // Encounter reference
            if (order.getEncounterRef() != null) {
                fhirResource.put("encounter", Map.of("reference", "Encounter/" + order.getEncounterRef()));
            }

            // Notes
            if (order.getClinicalNotes() != null) {
                fhirResource.put("note", java.util.List.of(Map.of("text", order.getClinicalNotes())));
            }

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(fhirResource, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String butanoRef = (String) response.getBody().get("id");
                log.info("BUTANO ServiceRequest created: orderId={}, butanoRef={}",
                        order.getOrderId(), butanoRef);
                return butanoRef;
            }

            log.warn("BUTANO returned non-success status {} for ServiceRequest creation, orderId={}",
                    response.getStatusCode(), order.getOrderId());
            return null;

        } catch (RestClientException e) {
            log.warn("BUTANO unavailable for ServiceRequest creation, orderId={}: {}",
                    order.getOrderId(), e.getMessage());
            return null;
        }
    }

    /**
     * Create a FHIR DiagnosticReport in BUTANO for a result.
     *
     * @param orderId the order this result belongs to
     * @param result  the result entity
     * @return the BUTANO reference, or null if BUTANO is unavailable
     */
    @SuppressWarnings("unchecked")
    public String createDiagnosticReport(String orderId, ResultEntity result) {
        try {
            String url = baseUrl + "/fhir/DiagnosticReport";

            Map<String, Object> fhirResource = new HashMap<>();
            fhirResource.put("resourceType", "DiagnosticReport");
            fhirResource.put("status", "final");

            fhirResource.put("basedOn", java.util.List.of(
                    Map.of("reference", "ServiceRequest/" + orderId)
            ));

            // Result category from kind
            fhirResource.put("category", java.util.List.of(Map.of(
                    "coding", java.util.List.of(Map.of(
                            "system", "urn:oros:result-kind",
                            "code", result.getKind().name()
                    ))
            )));

            if (result.getZiboResultCodes() != null) {
                fhirResource.put("code", Map.of(
                        "coding", java.util.List.of(Map.of(
                                "system", "urn:zibo:result-codes",
                                "code", result.getZiboResultCodes()
                        ))
                ));
            }

            fhirResource.put("conclusion", result.getSummary());

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(fhirResource, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String butanoRef = (String) response.getBody().get("id");
                log.info("BUTANO DiagnosticReport created: orderId={}, resultId={}, butanoRef={}",
                        orderId, result.getResultId(), butanoRef);
                return butanoRef;
            }

            log.warn("BUTANO returned non-success status {} for DiagnosticReport, orderId={}",
                    response.getStatusCode(), orderId);
            return null;

        } catch (RestClientException e) {
            log.warn("BUTANO unavailable for DiagnosticReport, orderId={}: {}",
                    orderId, e.getMessage());
            return null;
        }
    }

    /**
     * Create a FHIR DocumentReference in BUTANO for an attached document.
     *
     * @param orderId the order this document belongs to
     * @param docUrl  the document URL (from Landela/MinIO)
     * @return the BUTANO reference, or null if BUTANO is unavailable
     */
    @SuppressWarnings("unchecked")
    public String createDocumentReference(String orderId, String docUrl) {
        try {
            String url = baseUrl + "/fhir/DocumentReference";

            Map<String, Object> fhirResource = new HashMap<>();
            fhirResource.put("resourceType", "DocumentReference");
            fhirResource.put("status", "current");

            fhirResource.put("context", Map.of(
                    "related", java.util.List.of(
                            Map.of("reference", "ServiceRequest/" + orderId)
                    )
            ));

            fhirResource.put("content", java.util.List.of(Map.of(
                    "attachment", Map.of(
                            "url", docUrl,
                            "contentType", "application/pdf"
                    )
            )));

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(fhirResource, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String butanoRef = (String) response.getBody().get("id");
                log.info("BUTANO DocumentReference created: orderId={}, butanoRef={}",
                        orderId, butanoRef);
                return butanoRef;
            }

            log.warn("BUTANO returned non-success status {} for DocumentReference, orderId={}",
                    response.getStatusCode(), orderId);
            return null;

        } catch (RestClientException e) {
            log.warn("BUTANO unavailable for DocumentReference, orderId={}: {}",
                    orderId, e.getMessage());
            return null;
        }
    }

    /**
     * Build HTTP headers with trust context for inter-service communication.
     */
    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) {
                headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            }
        } catch (IllegalStateException e) {
            log.debug("No trust context available for BUTANO call headers");
        }
        return headers;
    }
}
