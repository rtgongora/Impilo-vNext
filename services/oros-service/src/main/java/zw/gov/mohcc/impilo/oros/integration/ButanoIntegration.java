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

import java.time.OffsetDateTime;
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
    private final boolean imagingStudyOutboundEnabled;

    public ButanoIntegration(RestTemplate restTemplate,
                             @Value("${oros.integration.butano.base-url:http://localhost:8090}") String baseUrl,
                             @Value("${oros.integration.fhir.imagingstudy-outbound.enabled:false}") boolean imagingStudyOutboundEnabled) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.imagingStudyOutboundEnabled = imagingStudyOutboundEnabled;
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
            fhirResource.put("status", fhirStatus(result));
            // Stable identifier so amendments can relatesTo prior versions across the SHR.
            fhirResource.put("identifier", java.util.List.of(Map.of(
                    "system", "https://impilo.gov.zw/oros/result-id",
                    "value", result.getResultId() != null ? result.getResultId().toString() : orderId
            )));

            fhirResource.put("basedOn", java.util.List.of(
                    Map.of("reference", "ServiceRequest/" + orderId)
            ));

            // Amendment/addendum lineage: link this version to the report it supersedes (§10).
            if (result.getSupersedesResultId() != null) {
                fhirResource.put("relatesTo", java.util.List.of(Map.of(
                        "code", relatesToCode(result),
                        "target", Map.of("identifier", Map.of(
                                "system", "https://impilo.gov.zw/oros/result-id",
                                "value", result.getSupersedesResultId().toString()))
                )));
            }

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

            fhirResource.put("conclusion",
                    result.getImpression() != null ? result.getImpression() : result.getSummary());

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(fhirResource, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String butanoRef = (String) response.getBody().get("id");
                log.info("BUTANO DiagnosticReport written: orderId={}, resultId={}, status={}, butanoRef={}",
                        orderId, result.getResultId(), fhirStatus(result), butanoRef);
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
     * Write the structured laboratory observations of a result to the SHR as FHIR Observation
     * resources (value[x] + UCUM unit + referenceRange + interpretation), each linked to the order's
     * ServiceRequest. Best-effort: a BUTANO outage stops early and is non-blocking.
     *
     * @return the count of observations successfully written
     */
    @SuppressWarnings("unchecked")
    public int createObservations(String orderId, ResultEntity result,
                                  java.util.List<zw.gov.mohcc.impilo.oros.persistence.entity.ResultObservationEntity> observations) {
        if (observations == null || observations.isEmpty()) {
            return 0;
        }
        String url = baseUrl + "/fhir/Observation";
        String obsStatus = "final".equals(fhirStatus(result)) ? "final" : "preliminary";
        int written = 0;
        for (var o : observations) {
            try {
                Map<String, Object> fhir = new HashMap<>();
                fhir.put("resourceType", "Observation");
                fhir.put("status", obsStatus);
                fhir.put("identifier", java.util.List.of(Map.of(
                        "system", "https://impilo.gov.zw/oros/observation-id",
                        "value", o.getObservationId() != null ? o.getObservationId().toString() : orderId)));
                fhir.put("basedOn", java.util.List.of(Map.of("reference", "ServiceRequest/" + orderId)));

                java.util.List<Map<String, Object>> coding = new java.util.ArrayList<>();
                if (o.getAnalyteCode() != null) {
                    coding.add(Map.of(
                            "system", o.getAnalyteSystem() != null ? o.getAnalyteSystem() : "urn:oros:analyte",
                            "code", o.getAnalyteCode(),
                            "display", o.getAnalyteName()));
                }
                Map<String, Object> code = new HashMap<>();
                code.put("coding", coding);
                code.put("text", o.getAnalyteName());
                fhir.put("code", code);

                if (o.getValueNumeric() != null) {
                    Map<String, Object> q = new HashMap<>();
                    q.put("value", o.getValueNumeric());
                    if (o.getUnit() != null) {
                        q.put("unit", o.getUnit());
                        q.put("system", "http://unitsofmeasure.org");
                        q.put("code", o.getUnit());
                    }
                    fhir.put("valueQuantity", q);
                } else if (o.getValueText() != null) {
                    fhir.put("valueString", o.getValueText());
                }

                if (o.getRefRangeLow() != null || o.getRefRangeHigh() != null || o.getRefRangeText() != null) {
                    Map<String, Object> rr = new HashMap<>();
                    if (o.getRefRangeLow() != null) rr.put("low", Map.of("value", o.getRefRangeLow()));
                    if (o.getRefRangeHigh() != null) rr.put("high", Map.of("value", o.getRefRangeHigh()));
                    if (o.getRefRangeText() != null) rr.put("text", o.getRefRangeText());
                    fhir.put("referenceRange", java.util.List.of(rr));
                }

                String interp = o.isCriticalFlag()
                        ? (o.getAbnormalFlag() != null ? o.getAbnormalFlag() : "AA")
                        : o.getAbnormalFlag();
                if (interp != null && !interp.isBlank() && !"N".equals(interp)) {
                    fhir.put("interpretation", java.util.List.of(Map.of("coding", java.util.List.of(Map.of(
                            "system", "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                            "code", interp)))));
                }

                HttpHeaders headers = buildTrustHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        url, new HttpEntity<>(fhir, headers), Map.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    written++;
                }
            } catch (RestClientException e) {
                log.warn("BUTANO unavailable for Observation, orderId={}: {}", orderId, e.getMessage());
                return written;
            }
        }
        log.info("BUTANO Observations written: orderId={}, count={}/{}", orderId, written, observations.size());
        return written;
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
     * Publish a FHIR R4 {@code ImagingStudy} to BUTANO when a study is linked to an order.
     *
     * <p>Flag-gated by {@code oros.integration.fhir.imagingstudy-outbound.enabled} — a no-op (and
     * reported NOT_LIVE at {@code /admin/integrations}) unless explicitly enabled. Degrades
     * gracefully if BUTANO is unreachable; never blocks the imaging workflow.</p>
     *
     * @param order    imaging order with a linked study ({@code studyUid})
     * @param modality optional DICOM modality code (XR, CT, MR, US, …)
     * @return the BUTANO reference, or null if disabled / no study / unavailable
     */
    @SuppressWarnings("unchecked")
    public String createImagingStudy(OrderEntity order, String modality) {
        if (!imagingStudyOutboundEnabled || order.getStudyUid() == null || order.getStudyUid().isBlank()) {
            return null;
        }
        try {
            String url = baseUrl + "/fhir/ImagingStudy";

            Map<String, Object> fhir = new HashMap<>();
            fhir.put("resourceType", "ImagingStudy");
            fhir.put("status", "available");

            java.util.List<Map<String, Object>> identifiers = new java.util.ArrayList<>();
            identifiers.add(Map.of("system", "urn:dicom:uid", "value", "urn:oid:" + order.getStudyUid()));
            if (order.getAccessionNumber() != null) {
                identifiers.add(Map.of(
                        "type", Map.of("coding", java.util.List.of(Map.of(
                                "system", "http://terminology.hl7.org/CodeSystem/v2-0203", "code", "ACSN"))),
                        "value", order.getAccessionNumber()));
            }
            fhir.put("identifier", identifiers);

            fhir.put("subject", Map.of(
                    "reference", "Patient/" + order.getPatientCpid(), "display", order.getPatientCpid()));
            fhir.put("started", (order.getScheduledAt() != null
                    ? order.getScheduledAt() : OffsetDateTime.now()).toString());
            fhir.put("basedOn", java.util.List.of(Map.of("reference", "ServiceRequest/" + order.getOrderId())));
            if (modality != null && !modality.isBlank()) {
                fhir.put("modality", java.util.List.of(Map.of(
                        "system", "http://dicom.nema.org/resources/ontology/DCM", "code", modality)));
            }
            if (order.getStudyViewerUrl() != null) {
                fhir.put("note", java.util.List.of(Map.of("text", "Viewer: " + order.getStudyViewerUrl())));
            }

            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(fhir, headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String ref = (String) response.getBody().get("id");
                log.info("BUTANO ImagingStudy written: orderId={}, studyUid={}, ref={}",
                        order.getOrderId(), order.getStudyUid(), ref);
                return ref;
            }
            log.warn("BUTANO returned non-success {} for ImagingStudy, orderId={}",
                    response.getStatusCode(), order.getOrderId());
            return null;

        } catch (RestClientException e) {
            log.warn("BUTANO unavailable for ImagingStudy, orderId={}: {}", order.getOrderId(), e.getMessage());
            return null;
        }
    }

    /** Map the OROS report lifecycle status onto a FHIR DiagnosticReport.status code. */
    private static String fhirStatus(ResultEntity result) {
        if (result.getReportStatus() == null) {
            return "final";
        }
        return switch (result.getReportStatus()) {
            case PRELIMINARY -> "preliminary";
            case FINAL -> "final";
            case AMENDED -> "amended";
            case CORRECTED -> "corrected";
            case ADDENDUM -> "appended";
        };
    }

    /** FHIR DiagnosticReport.relatesTo code for the supersession relationship. */
    private static String relatesToCode(ResultEntity result) {
        // An addendum appends to the prior report; an amendment/correction replaces it.
        return result.getReportStatus() == zw.gov.mohcc.impilo.oros.domain.ResultStatus.ADDENDUM
                ? "appends" : "replaces";
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
