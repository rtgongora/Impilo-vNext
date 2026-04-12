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
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
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
 * <p>Listens to VITO identity and dedup topics (legacy + v1.1 namespaces) and
 * consent revocation notifications. Processing is idempotent where possible:
 * duplicate patient creations and duplicate reconciliation mappings are skipped.</p>
 *
 * <p>Wire format:</p>
 * <ul>
 *   <li>Legacy VITO emits the inner JSON payload only (no envelope).</li>
 *   <li>V1.1 emits a canonical envelope JSON with nested {@code payload} (object or JSON string).</li>
 * </ul>
 */
@Component
public class ButanoEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ButanoEventConsumer.class);

    private static final String CPID_SYSTEM = "https://impilo.gov.zw/cpid";
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
     * VITO identity stream — client registration and lifecycle signals that affect the SHR Patient spine.
     */
    @KafkaListener(
            topics = {"vito.identity", "impilo.vito.identity"},
            groupId = "butano-shr"
    )
    public void consumeVitoIdentity(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String correlationId = extractCorrelationId(root);
            String envelopeEventType = extractEventType(root);
            JsonNode payload = extractPayload(root);

            log.info("BUTANO SHR: VITO identity event received envelopeType={} correlationId={}",
                    envelopeEventType, correlationId);

            if (payload == null || payload.isNull()) {
                log.warn("BUTANO SHR: identity event missing payload, skipping correlationId={}", correlationId);
                return;
            }

            IdentitySignal signal = resolveIdentitySignal(envelopeEventType, payload);
            log.debug("BUTANO SHR: resolved identity signal={} correlationId={}", signal, correlationId);

            String cpid = resolveSubjectIdentifier(payload);
            if (cpid == null || cpid.isBlank()) {
                log.warn("BUTANO SHR: identity event missing subject identifier (cpid/healthId), correlationId={}",
                        correlationId);
                return;
            }

            UUID tenantId = resolveTenantId(root, payload, cpid);
            if (tenantId == null && signal == IdentitySignal.CREATED) {
                if (findFirstPatientByCpid(cpid).isPresent()) {
                    log.debug("BUTANO SHR: duplicate create event, Patient already present for subject key={} "
                            + "correlationId={} (idempotent)", cpid, correlationId);
                    return;
                }
                log.warn("BUTANO SHR: cannot create Patient without tenant_id; subject key={} correlationId={}",
                        cpid, correlationId);
                return;
            }
            if (tenantId == null) {
                tenantId = inferTenantFromExistingPatient(cpid);
            }
            if (tenantId == null) {
                log.warn("BUTANO SHR: identity event missing tenant_id after inference, subject key={} "
                        + "correlationId={}", cpid, correlationId);
                return;
            }

            switch (signal) {
                case CREATED -> ensurePatientForCpid(tenantId, cpid, correlationId);
                case UPDATED_OR_VERIFIED -> updatePatientFromIdentityEvent(tenantId, cpid, payload, correlationId);
                case DECEASED -> markPatientInactiveIfPresent(tenantId, cpid, correlationId);
                case IGNORE -> log.debug("BUTANO SHR: ignoring identity event correlationId={}", correlationId);
            }
        } catch (JsonProcessingException e) {
            log.error("BUTANO SHR: failed to parse VITO identity event: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("BUTANO SHR: error handling VITO identity event: {}", e.getMessage(), e);
        }
    }

    /**
     * VITO merge / dedup stream — triggers CPID rekeying across FHIR resources.
     */
    @KafkaListener(
            topics = {"vito.dedup", "impilo.vito.dedup"},
            groupId = "butano-shr"
    )
    public void consumeVitoDedup(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String correlationId = extractCorrelationId(root);
            String eventType = extractEventType(root);
            JsonNode payload = extractPayload(root);

            if (payload == null || payload.isNull()) {
                log.warn("BUTANO SHR: dedup event missing payload, skipping correlationId={}", correlationId);
                return;
            }

            if (eventType != null && eventType.toLowerCase().contains("reversed")) {
                log.info("BUTANO SHR: merge reversal observed (no SHR reconcile) type={} correlationId={}",
                        eventType, correlationId);
                return;
            }

            if (!isMergeEvent(eventType, payload)) {
                log.debug("BUTANO SHR: dedup event not a merge, skipping type={} correlationId={}",
                        eventType, correlationId);
                return;
            }

            UUID tenantId = parseUuid(firstNonBlank(
                    text(payload, "tenant_id"),
                    text(payload, "tenantId"),
                    text(root, "tenant_id")));
            if (tenantId == null) {
                log.warn("BUTANO SHR: merge event missing tenant_id, skipping correlationId={}", correlationId);
                return;
            }

            String survivorCpid = firstNonBlank(
                    text(payload, "survivor_cpid"),
                    text(payload, "survivorCpid"),
                    text(payload, "survivorHealthId"),
                    text(payload, "survivor_health_id"),
                    text(payload, "survivor"));
            String mergedCpid = firstNonBlank(
                    text(payload, "merged_cpid"),
                    text(payload, "mergedCpid"),
                    text(payload, "mergedHealthId"),
                    text(payload, "merged_health_id"),
                    text(payload, "merged"));

            if (survivorCpid == null || mergedCpid == null) {
                log.warn("BUTANO SHR: merge event missing survivor/merged identifiers correlationId={}",
                        correlationId);
                return;
            }

            log.info("BUTANO SHR: VITO merge event survivor={} merged={} tenant={} correlationId={}",
                    survivorCpid, mergedCpid, tenantId, correlationId);

            triggerReconciliationIfNeeded(tenantId, mergedCpid, survivorCpid, correlationId);
        } catch (JsonProcessingException e) {
            log.error("BUTANO SHR: failed to parse VITO dedup event: {}", e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("BUTANO SHR: error handling VITO dedup event: {}", e.getMessage(), e);
        }
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
        if (payload.has("verifiedBy") && !payload.get("verifiedBy").isNull()) {
            patient.getMeta().addTag(new Coding(
                    "https://impilo.gov.zw/identity",
                    "verified",
                    "Identity verified in VITO"));
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

    private static String resolveSubjectIdentifier(JsonNode payload) {
        return firstNonBlank(
                text(payload, "patient_cpid"),
                text(payload, "patientCpid"),
                text(payload, "cpid"),
                text(payload, "healthId"),
                text(payload, "health_id"));
    }

    private UUID resolveTenantId(JsonNode root, JsonNode payload, String cpid) {
        UUID fromWire = parseUuid(firstNonBlank(
                text(payload, "tenant_id"),
                text(payload, "tenantId"),
                text(root, "tenant_id")));
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

    private Optional<Patient> findFirstPatientByCpid(String cpid) {
        return findPatientsByCpid(cpid, 1);
    }

    private enum IdentitySignal {
        CREATED,
        UPDATED_OR_VERIFIED,
        DECEASED,
        IGNORE
    }

    private static IdentitySignal resolveIdentitySignal(String envelopeEventType, JsonNode payload) {
        if (envelopeEventType != null) {
            String t = envelopeEventType.toLowerCase();
            if (t.contains("deceased")) {
                return IdentitySignal.DECEASED;
            }
            if (t.contains("verified")) {
                return IdentitySignal.UPDATED_OR_VERIFIED;
            }
            if (t.contains("updated")) {
                return IdentitySignal.UPDATED_OR_VERIFIED;
            }
            if (t.contains("created")) {
                return IdentitySignal.CREATED;
            }
            if (t.contains("identity_created") || t.contains("client_created")) {
                return IdentitySignal.CREATED;
            }
        }
        if (payload.has("deathNotificationRef") && !payload.get("deathNotificationRef").isNull()) {
            return IdentitySignal.DECEASED;
        }
        if (payload.has("verifiedBy") && !payload.get("verifiedBy").isNull()) {
            return IdentitySignal.UPDATED_OR_VERIFIED;
        }
        if ("PROVISIONAL".equalsIgnoreCase(text(payload, "status"))
                && payload.has("did") && !payload.get("did").isNull()) {
            return IdentitySignal.CREATED;
        }
        return IdentitySignal.IGNORE;
    }

    private static boolean isMergeEvent(String eventType, JsonNode payload) {
        if (eventType != null) {
            String t = eventType.toLowerCase();
            if (t.contains("merge") && !t.contains("reversed")) {
                return true;
            }
        }
        return payload.has("survivor") && payload.has("merged");
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText(null);
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
