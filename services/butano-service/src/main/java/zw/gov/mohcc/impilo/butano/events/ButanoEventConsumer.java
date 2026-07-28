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
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.ImagingStudy;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.DateTimeType;
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
import java.util.Locale;
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
    /** The originating PCT observation id, used as the business identifier for idempotent archival. */
    private static final String OBSERVATION_IDENTIFIER_SYSTEM = "https://impilo.gov.zw/pct/observation-id";
    private static final String PROBLEM_IDENTIFIER_SYSTEM = "https://impilo.gov.zw/pct/problem-id";

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


    /**
     * Archives discrete clinical observations recorded in pct-service into the SHR as FHIR
     * {@link Observation}.
     *
     * <p>This is the seam that makes the shared health record actually shared. Growth, immunisation
     * and labour registries are systems of record inside PCT; the SHR is where a clinician at
     * another facility sees them. Until this listener existed, PCT emitted observation events that
     * nothing consumed.</p>
     *
     * <p><strong>The SHR is CPID-only.</strong> The subject reference is resolved from the CPID to
     * an existing FHIR Patient, and nothing else about the person crosses this boundary — no name,
     * no date of birth, no national identifier. That is the platform's standing rule that
     * identifying data stays in VITO, and it is enforced here rather than assumed of the producer:
     * this consumer copies only the coded fields it knows about, so a producer that started leaking
     * a name into its payload still could not write one into the record.</p>
     *
     * <p>An observation with no value is still archived, carrying its dataAbsentReason. "Nobody took
     * the temperature" is a clinical fact worth sharing, and dropping such rows would make the SHR
     * quietly more reassuring than the source record.</p>
     *
     * <p>Idempotent by the originating observation id within the tenant tag. A corrected
     * observation re-published under the same id updates the existing resource rather than adding
     * a second one that disagrees with the first.</p>
     */
    @KafkaListener(
            topics = {"pct.observation.recorded", "impilo.pct.observation"},
            groupId = "butano-shr"
    )
    public void consumePctObservation(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String correlationId = extractCorrelationId(root);
            JsonNode payload = extractPayload(root);
            if (payload == null || payload.isNull()) {
                log.warn("BUTANO SHR: observation event missing payload, skipping correlationId={}",
                        correlationId);
                return;
            }

            String observationId = firstNonBlank(
                    text(payload, "observation_id"), text(payload, "observationId"));
            String patientCpid = firstNonBlank(
                    text(payload, "subject_cpid"), text(payload, "subjectCpid"), text(payload, "cpid"));
            String code = firstNonBlank(text(payload, "code"));

            if (observationId == null || patientCpid == null || code == null) {
                log.warn("BUTANO SHR: observation event missing observation_id, subject_cpid or code — "
                                + "skipping correlationId={}", correlationId);
                return;
            }

            String status = firstNonBlank(text(payload, "status"), "FINAL");
            if ("ENTERED_IN_ERROR".equalsIgnoreCase(status)) {
                // Deliberately not archived as a normal result. Withdrawing an already-shared
                // observation is a different operation and is not attempted silently here.
                log.info("BUTANO SHR: observation {} is entered-in-error; not archived as a result. "
                                + "correlationId={}", observationId, correlationId);
                return;
            }

            UUID tenantId = resolveTenantId(root, payload, patientCpid);
            if (tenantId == null) {
                log.warn("BUTANO SHR: observation event missing tenant_id, skipping observation_id={} "
                                + "correlationId={}", observationId, correlationId);
                return;
            }

            archiveObservation(tenantId, patientCpid, observationId, code, payload, correlationId);

        } catch (JsonProcessingException e) {
            log.error("BUTANO SHR: unreadable observation event: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("BUTANO SHR: failed to archive observation: {}", e.getMessage(), e);
        }
    }


    /**
     * Builds the FHIR Observation from the event payload.
     *
     * <p>Package-private and static so the boundary rule can be tested directly: this method is
     * the only thing that decides what crosses into the shared health record, and it copies an
     * explicit list of coded fields. A producer that began leaking a patient name, date of birth
     * or national identifier into its payload still could not write one here, because nothing
     * reads those keys. The rule is enforced by construction rather than trusted of the caller.</p>
     */
    static Observation buildObservation(String tenantTagSystem, UUID tenantId, String patientPid,
                                        String observationId, String code, JsonNode payload) {
        Observation obs = new Observation();
        obs.setMeta(new Meta().addTag(new Coding(tenantTagSystem, tenantId.toString(), null)));
        obs.setSubject(new Reference("Patient/" + patientPid));
        obs.addIdentifier().setSystem(OBSERVATION_IDENTIFIER_SYSTEM).setValue(observationId);

        String status = firstNonBlank(text(payload, "status"), "FINAL");
        obs.setStatus("AMENDED".equalsIgnoreCase(status)
                ? Observation.ObservationStatus.AMENDED
                : "PRELIMINARY".equalsIgnoreCase(status)
                        ? Observation.ObservationStatus.PRELIMINARY
                        : Observation.ObservationStatus.FINAL);

        String codeSystem = firstNonBlank(
                text(payload, "code_system"), text(payload, "codeSystem"),
                "https://impilo.gov.zw/clinical-observation");
        obs.setCode(new CodeableConcept().addCoding(
                new Coding(codeSystem, code, firstNonBlank(text(payload, "display"), null))));

        String category = firstNonBlank(text(payload, "category"), null);
        if (category != null) {
            obs.addCategory(new CodeableConcept().addCoding(
                    new Coding("http://terminology.hl7.org/CodeSystem/observation-category",
                            category.toLowerCase().replace('_', '-'), null)));
        }

        String effectiveAt = firstNonBlank(text(payload, "effective_at"), text(payload, "effectiveAt"));
        if (effectiveAt != null) {
            // Normalised through OffsetDateTime rather than handed straight to DateTimeType.
            // Java renders a zero-second timestamp as "2026-07-26T13:00Z", which FHIR rejects
            // because it requires seconds — so every observation recorded on the minute was
            // silently archived with no time at all. An observation without a time cannot be
            // ordered against the others, so the reader cannot tell whether the temperature was
            // taken before or after treatment.
            try {
                java.time.OffsetDateTime parsed = java.time.OffsetDateTime.parse(effectiveAt);
                obs.setEffective(new DateTimeType(java.util.Date.from(parsed.toInstant())));
            } catch (RuntimeException e) {
                log.warn("BUTANO SHR: observation {} has an unparseable effective_at '{}'; archived "
                                + "without a time rather than with a guessed one", observationId, effectiveAt);
            }
        }

        applyObservationValue(obs, payload, observationId);

        String interpretation = firstNonBlank(text(payload, "interpretation"), null);
        if (interpretation != null) {
            obs.addInterpretation(new CodeableConcept().addCoding(new Coding(
                    "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                    interpretation, null)));
        }

        return obs;
    }

    private void archiveObservation(UUID tenantId, String patientCpid, String observationId,
                                    String code, JsonNode payload, String correlationId) {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
        Optional<String> patientPid = findPatientId(patientDao, tenantId, patientCpid);
        if (patientPid.isEmpty()) {
            log.warn("BUTANO SHR: no FHIR Patient for CPID — cannot archive Observation {} tenant={} "
                            + "correlationId={}", observationId, tenantId, correlationId);
            return;
        }

        IFhirResourceDao<Observation> dao = daoRegistry.getResourceDao(Observation.class);
        Optional<String> existing = findObservationIdBySource(dao, tenantId, observationId);

        Observation obs = buildObservation(tenantTagSystem, tenantId, patientPid.get(),
                observationId, code, payload);
        if (existing.isPresent()) {
            obs.setId(new IdType("Observation", existing.get()));
            dao.update(obs, (RequestDetails) null);
            log.info("BUTANO SHR: updated Observation code={} observation_id={} patient_cpid={} tenant={} "
                            + "correlationId={}", code, observationId, patientCpid, tenantId, correlationId);
            return;
        }
        dao.create(obs, (RequestDetails) null);
        log.info("BUTANO SHR: archived Observation code={} observation_id={} patient_cpid={} tenant={} "
                        + "correlationId={}", code, observationId, patientCpid, tenantId, correlationId);
    }

    /**
     * Copies the one value shape the observation carries, or its absence reason.
     *
     * <p>An observation with neither is not written as an empty result: an Observation resource
     * with no value and no dataAbsentReason reads as a completed measurement whose result happens
     * to be missing, which is exactly the ambiguity the source table's CHECK constraint exists to
     * prevent. It is recorded as unknown instead.</p>
     */
    private static void applyObservationValue(Observation obs, JsonNode payload, String observationId) {
        JsonNode quantity = payload.get("value_quantity");
        String unit = firstNonBlank(text(payload, "value_unit"), text(payload, "valueUnit"));
        if (quantity != null && quantity.isNumber() && unit != null) {
            obs.setValue(new Quantity().setValue(quantity.decimalValue()).setUnit(unit));
            return;
        }
        String valueCode = firstNonBlank(text(payload, "value_code"), text(payload, "valueCode"));
        if (valueCode != null) {
            String valueCodeSystem = firstNonBlank(
                    text(payload, "value_code_system"), text(payload, "valueCodeSystem"),
                    "https://impilo.gov.zw/clinical-observation-value");
            obs.setValue(new CodeableConcept().addCoding(new Coding(valueCodeSystem, valueCode, null)));
            return;
        }
        JsonNode bool = payload.get("value_boolean");
        if (bool != null && bool.isBoolean()) {
            obs.setValue(new BooleanType(bool.booleanValue()));
            return;
        }
        String valueText = firstNonBlank(text(payload, "value_text"), text(payload, "valueText"));
        if (valueText != null) {
            obs.setValue(new StringType(valueText));
            return;
        }

        String absent = firstNonBlank(
                text(payload, "data_absent_reason"), text(payload, "dataAbsentReason"));
        if (absent == null) {
            log.warn("BUTANO SHR: observation {} arrived with neither a value nor a reason for its "
                            + "absence; recorded as unknown rather than as a blank result", observationId);
            absent = "UNKNOWN";
        }
        obs.setDataAbsentReason(new CodeableConcept().addCoding(new Coding(
                "http://terminology.hl7.org/CodeSystem/data-absent-reason",
                absent.toLowerCase().replace('_', '-'), null)));
    }

    /**
     * Archives the PCT problem list into the shared health record as FHIR Conditions.
     *
     * <p>Until this listener existed the problem list reached nobody. PCT emitted
     * PROBLEM_ADDED/_RESOLVED/_STATUS_CHANGED, but with no route case those events fell to the
     * {@code pct.events} catch-all and no consumer subscribed — so a diagnosis recorded at one
     * facility was absent from the SHR, from the IPS bundle a clinician reads at the next facility,
     * and from the cross-facility timeline. {@code Condition} was already listed in
     * TIMELINE_RESOURCES, RECONCILABLE_RESOURCES, STAT_RESOURCE_TYPES and IpsBundleGenerator, which
     * is what made the gap so easy to miss: the resource was readable everywhere and written
     * nowhere.</p>
     */
    @KafkaListener(topics = "pct.problem.recorded", groupId = "butano-shr")
    public void consumePctProblem(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String correlationId = extractCorrelationId(root);
            JsonNode payload = extractPayload(root);
            if (payload == null || payload.isNull()) {
                log.warn("BUTANO SHR: problem event missing payload, skipping correlationId={}", correlationId);
                return;
            }

            String problemId = firstNonBlank(text(payload, "problem_id"), text(payload, "problemId"));
            String patientCpid = firstNonBlank(
                    text(payload, "subject_cpid"), text(payload, "subjectCpid"), text(payload, "cpid"));
            String code = firstNonBlank(text(payload, "code"));

            if (problemId == null || patientCpid == null) {
                log.warn("BUTANO SHR: problem event missing problem_id or subject_cpid — skipping "
                        + "correlationId={}", correlationId);
                return;
            }
            if (code == null) {
                // A Condition with no coded value cannot be queried, mapped or reconciled — it would
                // occupy the record while answering no clinical question. Refused loudly rather than
                // archived as a text-only stub that looks like a diagnosis.
                log.warn("BUTANO SHR: problem {} has no code — not archived, because an uncoded "
                        + "Condition is not a queryable diagnosis. correlationId={}", problemId, correlationId);
                return;
            }

            UUID tenantId = resolveTenantId(root, payload, patientCpid);
            if (tenantId == null) {
                log.warn("BUTANO SHR: problem event missing tenant_id, skipping problem_id={} "
                        + "correlationId={}", problemId, correlationId);
                return;
            }

            archiveProblem(tenantId, patientCpid, problemId, code, payload, correlationId);

        } catch (JsonProcessingException e) {
            log.error("BUTANO SHR: unreadable problem event: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("BUTANO SHR: failed to archive problem: {}", e.getMessage(), e);
        }
    }

    private void archiveProblem(UUID tenantId, String patientCpid, String problemId,
                                String code, JsonNode payload, String correlationId) {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
        Optional<String> patientPid = findPatientId(patientDao, tenantId, patientCpid);
        if (patientPid.isEmpty()) {
            log.warn("BUTANO SHR: no FHIR Patient for CPID — cannot archive Condition {} tenant={} "
                    + "correlationId={}", problemId, tenantId, correlationId);
            return;
        }

        IFhirResourceDao<Condition> dao = daoRegistry.getResourceDao(Condition.class);
        Optional<String> existing = findConditionIdBySource(dao, tenantId, problemId);

        Condition condition = buildCondition(tenantTagSystem, tenantId, patientPid.get(),
                problemId, code, payload);
        if (existing.isPresent()) {
            // A status change is an update to the same diagnosis, never a second one. Keyed on the
            // PCT problem id so a resolve does not create a rival Condition alongside the active row.
            condition.setId(new IdType("Condition", existing.get()));
            dao.update(condition, (RequestDetails) null);
            log.info("BUTANO SHR: updated Condition code={} problem_id={} patient_cpid={} tenant={} "
                    + "correlationId={}", code, problemId, patientCpid, tenantId, correlationId);
            return;
        }
        dao.create(condition, (RequestDetails) null);
        log.info("BUTANO SHR: archived Condition code={} problem_id={} patient_cpid={} tenant={} "
                + "correlationId={}", code, problemId, patientCpid, tenantId, correlationId);
    }

    /**
     * Builds the FHIR Condition from the event payload.
     *
     * <p>Package-private and static for the same reason as {@link #buildObservation}: this method is
     * the only thing that decides what crosses into the shared health record, and it copies an
     * explicit list of coded fields. The PCT problem row carries free-text {@code notes}, which is
     * the one field that can hold PII — it is not on the event and nothing here reads it, so it
     * cannot cross even if a future producer started sending it. Enforced by construction rather
     * than trusted of the caller.</p>
     */
    static Condition buildCondition(String tenantTagSystem, UUID tenantId, String patientPid,
                                    String problemId, String code, JsonNode payload) {
        Condition condition = new Condition();
        condition.setMeta(new Meta().addTag(new Coding(tenantTagSystem, tenantId.toString(), null)));
        condition.setSubject(new Reference("Patient/" + patientPid));
        condition.addIdentifier().setSystem(PROBLEM_IDENTIFIER_SYSTEM).setValue(problemId);

        String system = firstNonBlank(text(payload, "code_system"), text(payload, "codeSystem"));
        String display = firstNonBlank(text(payload, "display"));
        condition.setCode(new CodeableConcept().addCoding(new Coding(system, code, display)));

        // PCT's clinical_status vocabulary is wider than FHIR's: RECURRENCE and RELAPSE are distinct
        // there and both map to FHIR "recurrence"/"relapse"; INACTIVE and RESOLVED are separate
        // states and must not be collapsed, because "no longer being treated" and "gone" are
        // different clinical claims. An unrecognised value is left unset rather than guessed —
        // a Condition with no clinical status is honest; one with the wrong one is not.
        String clinicalStatus = firstNonBlank(
                text(payload, "clinical_status"), text(payload, "clinicalStatus"));
        String fhirStatus = mapClinicalStatus(clinicalStatus);
        if (fhirStatus != null) {
            condition.setClinicalStatus(new CodeableConcept().addCoding(new Coding(
                    "http://terminology.hl7.org/CodeSystem/condition-clinical", fhirStatus, null)));
        }

        // Diagnostic certainty is the verification status. PCT deliberately allows it to be null
        // (never stated), and a null certainty must not become "confirmed" on the way to the SHR —
        // that would promote a suspicion into a diagnosis nobody made.
        String certainty = firstNonBlank(
                text(payload, "diagnostic_certainty"), text(payload, "diagnosticCertainty"));
        String verification = mapVerificationStatus(certainty);
        if (verification != null) {
            condition.setVerificationStatus(new CodeableConcept().addCoding(new Coding(
                    "http://terminology.hl7.org/CodeSystem/condition-ver-status", verification, null)));
        }

        String onset = firstNonBlank(text(payload, "onset_date"), text(payload, "onsetDate"));
        if (onset != null) {
            condition.setOnset(new DateTimeType(onset));
        }
        return condition;
    }

    /** PCT clinical status -> FHIR condition-clinical. Unknown values return null (left unset). */
    static String mapClinicalStatus(String pctStatus) {
        if (pctStatus == null) {
            return null;
        }
        return switch (pctStatus.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> "active";
            case "RECURRENCE" -> "recurrence";
            case "RELAPSE" -> "relapse";
            case "REMISSION" -> "remission";
            case "INACTIVE" -> "inactive";
            case "RESOLVED" -> "resolved";
            default -> null;
        };
    }

    /** PCT diagnostic certainty -> FHIR condition-ver-status. Null/unknown stay unset, never confirmed. */
    static String mapVerificationStatus(String certainty) {
        if (certainty == null) {
            return null;
        }
        return switch (certainty.toUpperCase(Locale.ROOT)) {
            case "SUSPECTED" -> "unconfirmed";
            case "DIFFERENTIAL" -> "differential";
            case "WORKING" -> "provisional";
            case "CONFIRMED" -> "confirmed";
            case "REFUTED" -> "refuted";
            default -> null;
        };
    }

    private Optional<String> findConditionIdBySource(IFhirResourceDao<Condition> dao, UUID tenantId,
                                                     String problemId) {
        SearchParameterMap map = new SearchParameterMap();
        map.add(Condition.SP_IDENTIFIER, new TokenParam(PROBLEM_IDENTIFIER_SYSTEM, problemId));
        map.add("_tag", new TokenParam(tenantTagSystem, tenantId.toString()));
        IBundleProvider results = dao.search(map);
        List<?> resources = results.getResources(0, 1);
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(((Condition) resources.get(0)).getIdElement().getIdPart());
    }

    private Optional<String> findObservationIdBySource(IFhirResourceDao<Observation> dao, UUID tenantId,
                                                       String observationId) {
        SearchParameterMap map = new SearchParameterMap();
        map.add(Observation.SP_IDENTIFIER,
                new TokenParam(OBSERVATION_IDENTIFIER_SYSTEM, observationId));
        map.add("_tag", new TokenParam(tenantTagSystem, tenantId.toString()));
        IBundleProvider results = dao.search(map);
        List<?> resources = results.getResources(0, 1);
        if (resources.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(((Observation) resources.get(0)).getIdElement().getIdPart());
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
