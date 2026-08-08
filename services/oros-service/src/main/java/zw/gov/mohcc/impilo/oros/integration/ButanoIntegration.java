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

    /** The SHR's CPID identifier system. Must stay identical to BUTANO's {@code CPID_SYSTEM}. */
    private static final String CPID_SYSTEM = "https://impilo.gov.zw/cpid";

    /**
     * The subject reference, as a FHIR conditional reference.
     *
     * <p>A CPID is not a FHIR logical id. {@code "Patient/" + cpid} is a dangling reference in
     * BUTANO, whose Patients carry server-assigned ids with the CPID in {@code Patient.identifier},
     * and BUTANO enforces referential integrity on write — measured 2026-08-08, that returns
     * HTTP 400 {@code HAPI-1094}. A match URL resolves against the identifier the SHR actually
     * stores, and fails closed for a CPID it does not know.</p>
     *
     * <p>Same construct as {@code ObservationShrWriter.patientSubjectReference} in telemonitoring
     * and {@code TeleconsultController.patientSubjectReference} in the BFF.</p>
     */
    /**
     * The subject for a resource that only knows its order id.
     *
     * <p>{@code createDiagnosticReport} and {@code createObservations} receive an {@code orderId}
     * and a {@code ResultEntity}, and the CPID is on neither — it lives on {@code OrderEntity}. So
     * both built resources with <b>no subject at all</b>, carrying only
     * {@code basedOn: ServiceRequest/{orderId}}. A clinical resource with no subject cannot be
     * found for a patient even if it is stored, and BUTANO never creates a ServiceRequest, so that
     * reference dangles and the write is refused outright.</p>
     *
     * <p>Resolving here rather than widening both signatures keeps the change off
     * {@code LabResultService}, which has no order access at all, and off
     * {@code ReportService}'s two call sites.</p>
     *
     * <p>Returns null when the order cannot be found. The caller omits the subject and the write
     * will be refused by BUTANO — which is correct: a clinical resource whose subject cannot be
     * established must not be filed against a guess.</p>
     */
    /** Business identifier for the originating OROS order. */
    private static final String ORDER_IDENTIFIER_SYSTEM = "https://impilo.gov.zw/oros/order-id";

    /**
     * Carries the originating order as a business identifier rather than a resource reference.
     *
     * <p>These resources previously set {@code basedOn: ServiceRequest/{orderId}}. BUTANO creates
     * no ServiceRequest — nothing in {@code ButanoEventConsumer} does, and no HTTP writer does
     * either — and it enforces referential integrity on write, so that reference dangles and the
     * whole resource is refused (HAPI-1094). Every lab result oros produced was rejected on it.</p>
     *
     * <p>An identifier loses nothing: the order is still recoverable from the record, and a
     * consumer can find every resource for an order by searching this system. If a ServiceRequest
     * anchor is later written into the SHR, {@code basedOn} can be restored as a match URL over
     * this same identifier — which is why the system URI is the order id and not an internal key.</p>
     */
    private static void addOrderIdentifier(Map<String, Object> resource, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> existing =
                (java.util.List<Map<String, Object>>) resource.get("identifier");
        java.util.List<Map<String, Object>> identifiers =
                existing == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(existing);
        identifiers.add(Map.of("system", ORDER_IDENTIFIER_SYSTEM, "value", orderId));
        resource.put("identifier", identifiers);
    }

    private String subjectForOrder(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .map(zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity::getPatientCpid)
                .filter(cpid -> cpid != null && !cpid.isBlank())
                .map(ButanoIntegration::patientSubjectReference)
                .orElseGet(() -> {
                    log.warn("OROS→SHR: no patient CPID for order {} — the resource is built without "
                            + "a subject and BUTANO will refuse it, rather than filing it against "
                            + "a guessed patient", orderId);
                    return null;
                });
    }

    private static String patientSubjectReference(String cpid) {
        return "Patient?identifier=" + CPID_SYSTEM + "|" + cpid;
    }

    private final RestTemplate restTemplate;
    private final zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository orderRepository;
    private final String baseUrl;
    private final boolean imagingStudyOutboundEnabled;

    public ButanoIntegration(RestTemplate restTemplate,
                             zw.gov.mohcc.impilo.oros.persistence.repository.OrderRepository orderRepository,
                             @Value("${oros.integration.butano.base-url:http://localhost:8090}") String baseUrl,
                             @Value("${oros.integration.fhir.imagingstudy-outbound.enabled:false}") boolean imagingStudyOutboundEnabled) {
        this.restTemplate = restTemplate;
        this.orderRepository = orderRepository;
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
                    "reference", patientSubjectReference(order.getPatientCpid())
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

            String subjectRef = subjectForOrder(orderId);
            if (subjectRef != null) {
                fhirResource.put("subject", Map.of("reference", subjectRef));
            }

            // basedOn is deliberately NOT set to ServiceRequest/{orderId}. BUTANO creates no
            // ServiceRequest, and it enforces referential integrity on write, so that reference
            // dangles and refuses the whole resource (HAPI-1094). The order is carried as a
            // business identifier below instead, which loses no information and resolves.
            addOrderIdentifier(fhirResource, orderId);

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
        // Resolved once, not per analyte: a lab result is many Observations for one patient.
        final String observationSubject = subjectForOrder(orderId);
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
                if (observationSubject != null) {
                    fhir.put("subject", Map.of("reference", observationSubject));
                }
                fhir.put("status", obsStatus);
                fhir.put("identifier", java.util.List.of(Map.of(
                        "system", "https://impilo.gov.zw/oros/observation-id",
                        "value", o.getObservationId() != null ? o.getObservationId().toString() : orderId)));
                addOrderIdentifier(fhir, orderId);

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
                    "reference", patientSubjectReference(order.getPatientCpid())));
            fhir.put("started", (order.getScheduledAt() != null
                    ? order.getScheduledAt() : OffsetDateTime.now()).toString());
            addOrderIdentifier(fhir, order.getOrderId());
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
     *
     * <p><b>Every field is null-guarded, and that is not defensive padding.</b>
     * {@code TrustContextFilter} never rejects a request — it builds a {@code TrustContext} from
     * whatever headers arrived, with {@code parseUuid} returning null for anything missing or
     * malformed. So {@code require()} succeeds and {@code ctx.tenantId()} is null, and
     * {@code ctx.tenantId().toString()} throws a {@link NullPointerException}.</p>
     *
     * <p>That NPE is not an {@link IllegalStateException}, so the catch below never saw it, and it
     * is not a {@code RestClientException}, so the outer catch in each {@code create*} method never
     * saw it either. It escaped all the way out of {@code createDiagnosticReport} — which
     * {@code ReportService.createFinal} calls <em>inside</em> {@code @Transactional}. A request
     * arriving without {@code x-tenant-id} therefore rolled back the report the clinician had just
     * authored, to fail a best-effort SHR write that was never going to succeed anyway.</p>
     *
     * <p>{@code X-Actor-Type} and {@code X-Purpose-Of-Use} are added because BUTANO's
     * {@code HeaderValidationInterceptor} rejects with 403 unless all five mandatory trust headers
     * are present. Without them this call could only ever have been refused.</p>
     */
    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.get();
            if (ctx == null) {
                log.debug("No trust context available for BUTANO call headers");
                return headers;
            }
            setIfPresent(headers, TrustContext.H_TENANT_ID, ctx.tenantId());
            setIfPresent(headers, TrustContext.H_ACTOR_ID, ctx.actorId());
            setIfPresent(headers, TrustContext.H_CORRELATION_ID, ctx.correlationId());
            setIfPresent(headers, TrustContext.H_FACILITY_ID, ctx.facilityId());
            // BUTANO's five mandatory trust headers. Absent these it answers 403 regardless of
            // everything else, so omitting them made the call unconditionally refusable.
            setIfPresent(headers, TrustContext.H_ACTOR_TYPE, ctx.actorType());
            setIfPresent(headers, TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse());
        } catch (RuntimeException e) {
            // Header assembly must never be able to fail the clinical transaction that called it.
            log.warn("Could not assemble trust headers for the BUTANO call ({}); the clinical "
                    + "record is unaffected", e.toString());
        }
        return headers;
    }

    private static void setIfPresent(HttpHeaders headers, String name, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString();
        if (!text.isBlank()) {
            headers.set(name, text);
        }
    }
}
