package zw.gov.mohcc.impilo.butano.events;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ImagingStudy;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.butano.core.ReconciliationService;
import zw.gov.mohcc.impilo.butano.persistence.entity.ReconciliationMappingEntity;
import zw.gov.mohcc.impilo.butano.persistence.repository.ReconciliationMappingRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kafka consumers for BUTANO (Shared Health Record) reacting to kernel events.
 *
 * <p>Listens to the clinical-plane subject lifecycle topic
 * ({@code impilo.identity.subject} — Identity Contract §7.3), PACS imaging study
 * metadata (legacy + v1.1), and consent revocation notifications. The SHR never
 * subscribes to identity-plane {@code vito.*} / {@code impilo.vito.*} topics:
 * those carry Health IDs, which must not reach clinical services. Processing is
 * idempotent where possible: duplicate patient creations and duplicate
 * reconciliation mappings are skipped.</p>
 */
@Component
public class ButanoEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ButanoEventConsumer.class);

    private static final String CPID_SYSTEM = "https://impilo.gov.zw/cpid";
    /** Accession number as the business identifier for idempotent SHR archival. */
    private static final String ACCESSION_IDENTIFIER_SYSTEM = "https://impilo.gov.zw/pacs/accession";
    private static final String REPORT_IDENTIFIER_SYSTEM = "https://impilo.gov.zw/pacs/report-ref";
    private static final String REQUESTED_BY_KAFKA = "kafka:butano-shr";

    private final ObjectMapper objectMapper;
    private final DaoRegistry daoRegistry;
    private final ReconciliationService reconciliationService;
    private final ReconciliationMappingRepository mappingRepository;

    @Value("${butano.tenant.tag-system:https://impilo.gov.zw/tenant}")
    private String tenantTagSystem;

    public ButanoEventConsumer(ObjectMapper objectMapper,
                               DaoRegistry daoRegistry,
                               ReconciliationService reconciliationService,
                               ReconciliationMappingRepository mappingRepository) {
        this.objectMapper = objectMapper;
        this.daoRegistry = daoRegistry;
        this.reconciliationService = reconciliationService;
        this.mappingRepository = mappingRepository;
    }

    /**
     * Clinical-plane subject lifecycle stream (Identity Contract §7.3).
     *
     * <p>The ONLY identity stream the SHR consumes. Produced exclusively by
     * tshepo-identity's SubjectTranslationRelay; payloads carry CPIDs only.
     * The former direct subscriptions to {@code vito.identity}/{@code vito.dedup}
     * are gone — those are identity-plane topics carrying Health IDs, which the
     * SHR must never see. There is deliberately no healthId fallback here: an
     * event without a {@code cpid} is a contract violation and is dropped.</p>
     */
    @KafkaListener(
            topics = "impilo.identity.subject",
            groupId = "butano-shr"
    )
    public void consumeSubjectLifecycle(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (root != null && root.isTextual()) {
                root = objectMapper.readTree(root.asText());
            }
            String correlationId = extractCorrelationId(root);
            String eventType = extractEventType(root);
            JsonNode payload = extractPayload(root);

            if (payload == null || payload.isNull()) {
                log.warn("BUTANO SHR: subject event missing payload, skipping correlationId={}", correlationId);
                return;
            }
            String action = subjectAction(eventType);
            if (action == null) {
                log.debug("BUTANO SHR: ignoring non-subject event type={} correlationId={}",
                        eventType, correlationId);
                return;
            }

            UUID tenantId = parseUuid(firstNonBlank(
                    text(payload, "tenant_id"), text(root, "tenant_id")));
            if (tenantId == null) {
                log.warn("BUTANO SHR: subject event missing tenant_id, skipping type={} correlationId={}",
                        eventType, correlationId);
                return;
            }

            switch (action) {
                case "created" -> {
                    String cpid = requireCpid(payload, "cpid", eventType, correlationId);
                    if (cpid != null) {
                        ensurePatientForCpid(tenantId, cpid, correlationId);
                    }
                }
                case "verified" -> {
                    String cpid = requireCpid(payload, "cpid", eventType, correlationId);
                    if (cpid != null) {
                        updatePatientFromIdentityEvent(tenantId, cpid, payload, correlationId);
                    }
                }
                case "deceased" -> {
                    String cpid = requireCpid(payload, "cpid", eventType, correlationId);
                    if (cpid != null) {
                        markPatientInactiveIfPresent(tenantId, cpid, correlationId);
                    }
                }
                case "merged" -> {
                    String survivorCpid = requireCpid(payload, "survivor_cpid", eventType, correlationId);
                    String mergedCpid = requireCpid(payload, "merged_cpid", eventType, correlationId);
                    if (survivorCpid != null && mergedCpid != null) {
                        log.info("BUTANO SHR: subject merge survivor={} merged={} tenant={} correlationId={}",
                                survivorCpid, mergedCpid, tenantId, correlationId);
                        triggerReconciliationIfNeeded(tenantId, mergedCpid, survivorCpid, correlationId);
                    }
                }
                case "merge_reversed" -> log.info(
                        "BUTANO SHR: merge reversal observed (no SHR reconcile) correlationId={}", correlationId);
                case "reconciled" -> {
                    String provisionalCpid = requireCpid(payload, "provisional_cpid", eventType, correlationId);
                    String canonicalCpid = requireCpid(payload, "canonical_cpid", eventType, correlationId);
                    if (provisionalCpid != null && canonicalCpid != null) {
                        log.info("BUTANO SHR: O-CPID reconcile provisional={} canonical={} tenant={} "
                                + "correlationId={}", provisionalCpid, canonicalCpid, tenantId, correlationId);
                        triggerReconciliationIfNeeded(tenantId, provisionalCpid, canonicalCpid, correlationId);
                    }
                }
                default -> log.debug("BUTANO SHR: unhandled subject action={} correlationId={}",
                        action, correlationId);
            }
        } catch (JsonProcessingException e) {
            log.error("BUTANO SHR: failed to parse subject event: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("BUTANO SHR: error handling subject event: {}", e.getMessage(), e);
        }
    }

    /** Extracts the action from {@code impilo.identity.subject.<action>.v1}, or null. */
    private static String subjectAction(String eventType) {
        if (eventType == null) {
            return null;
        }
        String t = eventType.toLowerCase();
        int idx = t.indexOf(".subject.");
        if (idx < 0) {
            return null;
        }
        String rest = t.substring(idx + ".subject.".length());
        int versionDot = rest.lastIndexOf(".v");
        return versionDot > 0 ? rest.substring(0, versionDot) : rest;
    }

    private static String requireCpid(JsonNode payload, String field, String eventType, String correlationId) {
        String value = text(payload, field);
        if (value == null || value.isBlank()) {
            log.warn("BUTANO SHR: subject event missing {} (contract violation), type={} correlationId={}",
                    field, eventType, correlationId);
            return null;
        }
        return value;
    }

    /**
     * Consent platform stream — informational audit trail for SHR (enforcement is at FHIR Gateway).
     */
    @KafkaListener(
            topics = "platform.consent.events",
            groupId = "butano-shr"
    )
    public void consumeConsentEvents(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String correlationId = extractCorrelationId(root);
            JsonNode payload = extractPayload(root);
            if (payload == null || payload.isNull()) {
                payload = root;
            }

            String status = text(payload, "status");
            String consentId = text(payload, "consentId");
            String tenantId = text(payload, "tenantId");
            String patientRef = text(payload, "patientRef");

            if ("REVOKED".equalsIgnoreCase(status)) {
                log.info("BUTANO SHR: consent revocation observed consentId={} tenantId={} patientRef={} "
                                + "correlationId={}",
                        consentId, tenantId, patientRef, correlationId);
            } else {
                log.debug("BUTANO SHR: consent event status={} consentId={} correlationId={}",
                        status, consentId, correlationId);
            }
        } catch (JsonProcessingException e) {
            log.error("BUTANO SHR: failed to parse consent event: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("BUTANO SHR: error handling consent event: {}", e.getMessage(), e);
        }
    }

    /**
     * PACS imaging study stream — archives {@code pacs.study.available} / {@code pacs.study.correlated}
     * (and legacy / v1.1 routing topics) into the SHR as FHIR {@link ImagingStudy}.
     *
     * <p>Idempotency is by accession number (identifier) within the tenant tag. Legacy emits the inner
     * JSON only; v1.1 uses the canonical envelope.</p>
     */
    @KafkaListener(
            topics = {
                    "pacs.study.available",
                    "pacs.study.correlated",
                    "pacs.imaging_study",
                    "impilo.pacs.imaging_study"
            },
            groupId = "butano-shr"
    )
    public void consumePacsImagingStudy(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String correlationId = extractCorrelationId(root);
            JsonNode payload = extractPayload(root);
            if (payload == null || payload.isNull()) {
                log.warn("BUTANO SHR: PACS imaging event missing payload, skipping correlationId={}",
                        correlationId);
                return;
            }

            String eventType = firstNonBlank(
                    extractEventType(root),
                    text(payload, "eventType"),
                    "pacs.study");

            String studyId = jsonScalarAsText(payload, "study_id");
            String patientCpid = firstNonBlank(
                    text(payload, "patient_cpid"),
                    text(payload, "patientCpid"),
                    text(payload, "cpid"));
            String modality = firstNonBlank(text(payload, "modality"), "OT");
            String accessionNumber = firstNonBlank(
                    text(payload, "accession_number"),
                    text(payload, "accessionNumber"));

            if (patientCpid == null || patientCpid.isBlank()) {
                log.warn("BUTANO SHR: PACS event missing patient_cpid, skipping eventType={} correlationId={}",
                        eventType, correlationId);
                return;
            }

            UUID tenantId = resolveTenantId(root, payload, patientCpid);
            if (tenantId == null) {
                log.warn("BUTANO SHR: PACS event missing tenant_id, skipping eventType={} study_id={} "
                                + "correlationId={}",
                        eventType, studyId, correlationId);
                return;
            }

            if (isReportLinkedEvent(eventType, payload)) {
                archiveDiagnosticReportIfAbsent(
                        tenantId,
                        patientCpid,
                        firstNonBlank(text(payload, "report_ref"), text(payload, "reportRef")),
                        accessionNumber,
                        firstNonBlank(text(payload, "studyInstanceUid"), text(payload, "study_instance_uid")),
                        firstNonBlank(text(payload, "oros_order_id"), text(payload, "orosOrderId")),
                        payload,
                        correlationId);
                return;
            }

            if (accessionNumber == null || accessionNumber.isBlank()) {
                log.warn("BUTANO SHR: PACS event missing accession_number, skipping eventType={} "
                                + "study_id={} patient_cpid={} modality={} correlationId={}",
                        eventType, studyId, patientCpid, modality, correlationId);
                return;
            }

            if (isConsentDenied(payload)) {
                log.info("BUTANO SHR: skipping PACS imaging archival for denied/revoked consent "
                                + "accession={} correlationId={}",
                        accessionNumber, correlationId);
                return;
            }

            String studyInstanceUid = firstNonBlank(
                    text(payload, "studyInstanceUid"),
                    text(payload, "study_instance_uid"));

            archivePacsImagingStudyIfAbsent(tenantId, patientCpid, studyId, modality, accessionNumber,
                    studyInstanceUid, payload, eventType, correlationId);
        } catch (JsonProcessingException e) {
            log.error("BUTANO SHR: failed to parse PACS imaging event: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("BUTANO SHR: error handling PACS imaging event: {}", e.getMessage(), e);
        }
    }

    private void archivePacsImagingStudyIfAbsent(UUID tenantId, String patientCpid, String studyId,
                                                 String modality, String accessionNumber,
                                                 String studyInstanceUid, JsonNode payload,
                                                 String eventType, String correlationId) {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
        Optional<String> patientPid = findPatientId(patientDao, tenantId, patientCpid);
        if (patientPid.isEmpty()) {
            log.warn("BUTANO SHR: no FHIR Patient for CPID — cannot archive ImagingStudy accession={} "
                            + "tenant={} correlationId={}",
                    accessionNumber, tenantId, correlationId);
            return;
        }

        IFhirResourceDao<ImagingStudy> studyDao = daoRegistry.getResourceDao(ImagingStudy.class);
        if (findImagingStudyIdByAccession(studyDao, tenantId, accessionNumber).isPresent()) {
            log.info("BUTANO SHR: PACS study already in SHR (idempotent skip) accession={} study_id={} "
                            + "patient_cpid={} modality={} tenant={} eventType={} correlationId={}",
                    accessionNumber, studyId, patientCpid, modality, tenantId, eventType, correlationId);
            return;
        }

        ImagingStudy study = new ImagingStudy();
        study.setMeta(new Meta().addTag(new Coding(tenantTagSystem, tenantId.toString(), null)));
        study.setStatus(ImagingStudy.ImagingStudyStatus.AVAILABLE);
        study.setSubject(new Reference("Patient/" + patientPid.get()));
        study.addIdentifier().setSystem(ACCESSION_IDENTIFIER_SYSTEM).setValue(accessionNumber);

        study.getModality().clear();
        study.addModality(new Coding("http://dicom.nema.org/resources/ontology/DCM", modality, null));

        if (studyInstanceUid != null && !studyInstanceUid.isBlank()) {
            study.addIdentifier().setSystem("urn:dicom:uid").setValue(studyInstanceUid);
        }

        study.setDescription(firstNonBlank(
                text(payload, "reportSummary"),
                text(payload, "description"),
                "Imaging study"));

        study.setNumberOfSeries(1);
        study.setNumberOfInstances(1);
        String seriesBase = (studyInstanceUid != null && !studyInstanceUid.isBlank())
                ? studyInstanceUid
                : accessionNumber;
        ImagingStudy.ImagingStudySeriesComponent series = study.addSeries();
        series.setUid(seriesBase + ".series1");
        series.setModality(new Coding("http://dicom.nema.org/resources/ontology/DCM", modality, null));
        ImagingStudy.ImagingStudySeriesInstanceComponent inst = series.addInstance();
        inst.setUid(seriesBase + ".1");

        studyDao.create(study, (RequestDetails) null);
        log.info("BUTANO SHR: archived PACS ImagingStudy accession={} study_id={} patient_cpid={} modality={} "
                        + "tenant={} eventType={} correlationId={}",
                accessionNumber, studyId, patientCpid, modality, tenantId, eventType, correlationId);
    }

    private void archiveDiagnosticReportIfAbsent(UUID tenantId,
                                                 String patientCpid,
                                                 String reportRef,
                                                 String accessionNumber,
                                                 String studyInstanceUid,
                                                 String orderRef,
                                                 JsonNode payload,
                                                 String correlationId) {
        if (reportRef == null || reportRef.isBlank()) {
            log.warn("BUTANO SHR: PACS report-linked event missing report_ref, skipping correlationId={}",
                    correlationId);
            return;
        }
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
        Optional<String> patientPid = findPatientId(patientDao, tenantId, patientCpid);
        if (patientPid.isEmpty()) {
            log.warn("BUTANO SHR: no FHIR Patient for CPID — cannot archive DiagnosticReport reportRef={} "
                            + "tenant={} correlationId={}",
                    reportRef, tenantId, correlationId);
            return;
        }

        IFhirResourceDao<DiagnosticReport> reportDao = daoRegistry.getResourceDao(DiagnosticReport.class);
        if (findDiagnosticReportIdByRef(reportDao, tenantId, reportRef).isPresent()) {
            log.info("BUTANO SHR: DiagnosticReport already archived (idempotent skip) reportRef={} tenant={} "
                            + "correlationId={}",
                    reportRef, tenantId, correlationId);
            return;
        }

        DiagnosticReport report = new DiagnosticReport();
        report.setMeta(new Meta().addTag(new Coding(tenantTagSystem, tenantId.toString(), null)));
        report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
        report.setSubject(new Reference("Patient/" + patientPid.get()));
        report.addIdentifier().setSystem(REPORT_IDENTIFIER_SYSTEM).setValue(reportRef);
        if (accessionNumber != null && !accessionNumber.isBlank()) {
            report.addIdentifier().setSystem(ACCESSION_IDENTIFIER_SYSTEM).setValue(accessionNumber);
        }
        report.setCode(new CodeableConcept().addCoding(
                new Coding("http://loinc.org", "18748-4", "Diagnostic imaging study")));
        report.setConclusion(firstNonBlank(
                text(payload, "report_status"),
                text(payload, "reportStatus"),
                text(payload, "reportSummary"),
                "Imaging report linked"));

        if (orderRef != null && !orderRef.isBlank()) {
            report.addBasedOn(new Reference("ServiceRequest/" + orderRef));
        }
        Optional<String> imagingStudyId = findImagingStudyIdByAccessionOrUid(
                daoRegistry.getResourceDao(ImagingStudy.class), tenantId, accessionNumber, studyInstanceUid);
        imagingStudyId.ifPresent(id -> report.addImagingStudy(new Reference("ImagingStudy/" + id)));

        if (isHttpReference(reportRef)) {
            IFhirResourceDao<DocumentReference> docDao = daoRegistry.getResourceDao(DocumentReference.class);
            if (findDocumentReferenceIdByReportRef(docDao, tenantId, reportRef).isEmpty()) {
                DocumentReference doc = new DocumentReference();
                doc.setMeta(new Meta().addTag(new Coding(tenantTagSystem, tenantId.toString(), null)));
                doc.setStatus(Enumerations.DocumentReferenceStatus.CURRENT);
                doc.setSubject(new Reference("Patient/" + patientPid.get()));
                doc.addIdentifier().setSystem(REPORT_IDENTIFIER_SYSTEM).setValue(reportRef);
                DocumentReference.DocumentReferenceContentComponent content = doc.addContent();
                content.getAttachment().setUrl(reportRef);
                content.getAttachment().setContentType("application/pdf");
                docDao.create(doc, (RequestDetails) null);
            }
        }

        reportDao.create(report, (RequestDetails) null);
        log.info("BUTANO SHR: archived DiagnosticReport reportRef={} accession={} patient_cpid={} tenant={} "
                        + "correlationId={}",
                reportRef, accessionNumber, patientCpid, tenantId, correlationId);
    }

    private Optional<String> findImagingStudyIdByAccession(IFhirResourceDao<ImagingStudy> studyDao, UUID tenantId,
                                                           String accessionNumber) {
        SearchParameterMap params = new SearchParameterMap();
        params.add("identifier", new TokenParam(ACCESSION_IDENTIFIER_SYSTEM, accessionNumber));
        params.add("_tag", new TokenParam(tenantTagSystem, tenantId.toString()));
        params.setCount(1);
        IBundleProvider results = studyDao.search(params, (RequestDetails) null);
        List<?> resources = results.getResources(0, 1);
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        ImagingStudy st = (ImagingStudy) resources.get(0);
        return Optional.ofNullable(st.getIdElement().getIdPart());
    }

    private Optional<String> findImagingStudyIdByAccessionOrUid(IFhirResourceDao<ImagingStudy> studyDao,
                                                                 UUID tenantId,
                                                                 String accessionNumber,
                                                                 String studyInstanceUid) {
        if (accessionNumber != null && !accessionNumber.isBlank()) {
            Optional<String> byAccession = findImagingStudyIdByAccession(studyDao, tenantId, accessionNumber);
            if (byAccession.isPresent()) {
                return byAccession;
            }
        }
        if (studyInstanceUid == null || studyInstanceUid.isBlank()) {
            return Optional.empty();
        }
        SearchParameterMap params = new SearchParameterMap();
        params.add("identifier", new TokenParam("urn:dicom:uid", studyInstanceUid));
        params.add("_tag", new TokenParam(tenantTagSystem, tenantId.toString()));
        params.setCount(1);
        IBundleProvider results = studyDao.search(params, (RequestDetails) null);
        List<?> resources = results.getResources(0, 1);
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        ImagingStudy st = (ImagingStudy) resources.get(0);
        return Optional.ofNullable(st.getIdElement().getIdPart());
    }

    private Optional<String> findDiagnosticReportIdByRef(IFhirResourceDao<DiagnosticReport> reportDao,
                                                          UUID tenantId,
                                                          String reportRef) {
        SearchParameterMap params = new SearchParameterMap();
        params.add("identifier", new TokenParam(REPORT_IDENTIFIER_SYSTEM, reportRef));
        params.add("_tag", new TokenParam(tenantTagSystem, tenantId.toString()));
        params.setCount(1);
        IBundleProvider results = reportDao.search(params, (RequestDetails) null);
        List<?> resources = results.getResources(0, 1);
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        DiagnosticReport report = (DiagnosticReport) resources.get(0);
        return Optional.ofNullable(report.getIdElement().getIdPart());
    }

    private Optional<String> findDocumentReferenceIdByReportRef(IFhirResourceDao<DocumentReference> docDao,
                                                                 UUID tenantId,
                                                                 String reportRef) {
        SearchParameterMap params = new SearchParameterMap();
        params.add("identifier", new TokenParam(REPORT_IDENTIFIER_SYSTEM, reportRef));
        params.add("_tag", new TokenParam(tenantTagSystem, tenantId.toString()));
        params.setCount(1);
        IBundleProvider results = docDao.search(params, (RequestDetails) null);
        List<?> resources = results.getResources(0, 1);
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        DocumentReference doc = (DocumentReference) resources.get(0);
        return Optional.ofNullable(doc.getIdElement().getIdPart());
    }

    private static boolean isConsentDenied(JsonNode payload) {
        String consentStatus = text(payload, "consentStatus");
        if (consentStatus != null && (consentStatus.equalsIgnoreCase("REVOKED")
                || consentStatus.equalsIgnoreCase("DENIED"))) {
            return true;
        }
        if (payload.has("consentGranted") && payload.get("consentGranted").isBoolean()) {
            return !payload.get("consentGranted").asBoolean();
        }
        return false;
    }

    private static boolean isReportLinkedEvent(String eventType, JsonNode payload) {
        String t = eventType != null ? eventType.toLowerCase() : "";
        if (t.contains("report.linked")) {
            return true;
        }
        return payload.has("report_ref") || payload.has("reportRef");
    }

    private static boolean isHttpReference(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    private void ensurePatientForCpid(UUID tenantId, String cpid, String correlationId) {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
        if (findPatientId(patientDao, tenantId, cpid).isPresent()) {
            log.debug("BUTANO SHR: Patient already exists for subject key={}, tenant={}, correlationId={} "
                            + "(idempotent)",
                    cpid, tenantId, correlationId);
            return;
        }

        Patient patient = new Patient();
        patient.setActive(true);
        patient.setMeta(new Meta().addTag(new Coding(tenantTagSystem, tenantId.toString(), null)));
        patient.addIdentifier()
                .setSystem(CPID_SYSTEM)
                .setValue(cpid);

        patientDao.create(patient, (RequestDetails) null);
        log.info("BUTANO SHR: created FHIR Patient for subject key={} tenant={} correlationId={}",
                cpid, tenantId, correlationId);
    }

    private void updatePatientFromIdentityEvent(UUID tenantId, String cpid, JsonNode payload,
                                                String correlationId) {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
        Optional<String> pid = findPatientId(patientDao, tenantId, cpid);
        if (pid.isEmpty()) {
            log.warn("BUTANO SHR: update skipped, no Patient for subject key={} tenant={} correlationId={}",
                    cpid, tenantId, correlationId);
            return;
        }

        Patient patient = patientDao.read(new IdType("Patient", pid.get()), (RequestDetails) null);
        if (patient.getMeta() == null) {
            patient.setMeta(new Meta());
        }
        patient.setActive(true);
        if (payload.path("verified").asBoolean(false)
                || (payload.has("verifiedBy") && !payload.get("verifiedBy").isNull())) {
            patient.getMeta().addTag(new Coding(
                    "https://impilo.gov.zw/identity",
                    "verified",
                    "Identity verified in the client registry"));
        }
        patientDao.update(patient, (RequestDetails) null);
        log.info("BUTANO SHR: updated FHIR Patient for subject key={} tenant={} correlationId={}",
                cpid, tenantId, correlationId);
    }

    private void markPatientInactiveIfPresent(UUID tenantId, String cpid, String correlationId) {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
        Optional<String> pid = findPatientId(patientDao, tenantId, cpid);
        if (pid.isEmpty()) {
            log.debug("BUTANO SHR: deceased event but no Patient for subject key={} tenant={} correlationId={}",
                    cpid, tenantId, correlationId);
            return;
        }
        Patient patient = patientDao.read(new IdType("Patient", pid.get()), (RequestDetails) null);
        patient.setActive(false);
        patientDao.update(patient, (RequestDetails) null);
        log.info("BUTANO SHR: marked Patient inactive (deceased) subject key={} tenant={} correlationId={}",
                cpid, tenantId, correlationId);
    }

    private void triggerReconciliationIfNeeded(UUID tenantId, String mergedCpid, String survivorCpid,
                                                 String correlationId) {
        Optional<ReconciliationMappingEntity> existing =
                mappingRepository.findByTenantIdAndOldCpid(tenantId, mergedCpid);
        if (existing.isPresent()) {
            log.info("BUTANO SHR: reconciliation already recorded for oldCpid={}, skipping correlationId={}",
                    mergedCpid, correlationId);
            return;
        }

        ReconciliationMappingEntity mapping = new ReconciliationMappingEntity();
        mapping.setTenantId(tenantId);
        mapping.setOldCpid(mergedCpid);
        mapping.setNewCpid(survivorCpid);
        mapping.setRequestedBy(REQUESTED_BY_KAFKA);
        mapping.setCorrelationId(correlationId);
        mapping = mappingRepository.save(mapping);

        reconciliationService.reconcile(mapping);
        log.info("BUTANO SHR: queued reconciliation mapping id={} merged={} -> survivor={} correlationId={}",
                mapping.getId(), mergedCpid, survivorCpid, correlationId);
    }

    private Optional<String> findPatientId(IFhirResourceDao<Patient> patientDao, UUID tenantId, String cpid) {
        SearchParameterMap params = new SearchParameterMap();
        params.add("identifier", new TokenParam(CPID_SYSTEM, cpid));
        params.add("_tag", new TokenParam(tenantTagSystem, tenantId.toString()));
        params.setCount(1);
        IBundleProvider results = patientDao.search(params, (RequestDetails) null);
        List<?> resources = results.getResources(0, 1);
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        Patient p = (Patient) resources.get(0);
        return Optional.ofNullable(p.getIdElement().getIdPart());
    }

    private JsonNode extractPayload(JsonNode root) {
        if (!root.has("payload")) {
            return root;
        }
        JsonNode p = root.get("payload");
        if (p == null || p.isNull()) {
            return null;
        }
        if (p.isObject()) {
            return p;
        }
        if (p.isTextual()) {
            try {
                return objectMapper.readTree(p.asText());
            } catch (JsonProcessingException e) {
                log.warn("BUTANO SHR: failed to parse textual payload envelope: {}", e.getMessage());
                return null;
            }
        }
        return p;
    }

    private static String extractEventType(JsonNode root) {
        if (root.has("event_type") && !root.get("event_type").isNull()) {
            return root.get("event_type").asText();
        }
        if (root.has("eventType") && !root.get("eventType").isNull()) {
            return root.get("eventType").asText();
        }
        return null;
    }

    private static String extractCorrelationId(JsonNode root) {
        String c = firstNonBlank(text(root, "correlation_id"), text(root, "correlationId"));
        if (c != null) {
            return c;
        }
        JsonNode payload = root.path("payload");
        if (payload.isObject()) {
            return firstNonBlank(text(payload, "correlation_id"), text(payload, "correlationId"));
        }
        return null;
    }

    private UUID resolveTenantId(JsonNode root, JsonNode payload, String cpid) {
        UUID fromWire = parseUuid(firstNonBlank(
                text(root, "tenant_id"),
                text(root, "tenantId"),
                text(payload, "tenant_id"),
                text(payload, "tenantId")));
        if (fromWire != null) {
            return fromWire;
        }
        return inferTenantFromExistingPatient(cpid);
    }

    private UUID inferTenantFromExistingPatient(String cpid) {
        Optional<Patient> only = findPatientsByCpid(cpid, 2);
        if (only.isEmpty()) {
            return null;
        }
        Patient p = only.get();
        if (p.getMeta() == null || p.getMeta().getTag().isEmpty()) {
            return null;
        }
        for (Coding tag : p.getMeta().getTag()) {
            if (tenantTagSystem.equals(tag.getSystem()) && tag.getCode() != null) {
                return parseUuid(tag.getCode());
            }
        }
        return null;
    }

    /**
     * Returns the single Patient for a CPID identifier, or empty if none / ambiguous.
     */
    private Optional<Patient> findPatientsByCpid(String cpid, int max) {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
        SearchParameterMap params = new SearchParameterMap();
        params.add("identifier", new TokenParam(CPID_SYSTEM, cpid));
        params.setCount(max);
        IBundleProvider results = patientDao.search(params, (RequestDetails) null);
        List<?> resources = results.getResources(0, max);
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        if (resources.size() > 1) {
            log.warn("BUTANO SHR: ambiguous CPID lookup for subject key={} ({} matches)", cpid, resources.size());
            return Optional.empty();
        }
        return Optional.of((Patient) resources.get(0));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText(null);
    }

    /** JSON number or string field rendered as text (e.g. {@code study_id}). */
    private static String jsonScalarAsText(JsonNode payload, String field) {
        if (payload == null || !payload.has(field) || payload.get(field).isNull()) {
            return null;
        }
        JsonNode n = payload.get(field);
        if (n.isTextual()) {
            return n.asText(null);
        }
        if (n.isNumber()) {
            return n.asText();
        }
        return n.asText(null);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
