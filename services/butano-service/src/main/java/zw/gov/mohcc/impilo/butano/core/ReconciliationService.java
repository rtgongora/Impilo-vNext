package zw.gov.mohcc.impilo.butano.core;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.butano.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.butano.persistence.entity.ReconciliationMappingEntity;
import zw.gov.mohcc.impilo.butano.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.butano.persistence.repository.ReconciliationMappingRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Reconciles O-CPID (provisional CPID) to permanent CPID by rewriting
 * subject/patient references across all FHIR resources.
 *
 * When a patient's identity is verified and assigned a permanent CPID in VITO,
 * this service finds all FHIR resources that reference the old provisional Patient
 * and rewrites their subject/patient references to point to the new permanent
 * Patient resource. A provenance tag is added to each rewritten resource.
 *
 * The reconciliation runs asynchronously and publishes a Kafka event on completion
 * via the transactional outbox pattern.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private static final String CPID_SYSTEM = "https://impilo.gov.zw/cpid";
    private static final String RECONCILED_TAG_SYSTEM = "https://impilo.gov.zw/reconciled";
    private static final String EVENT_TYPE_RECONCILE = "RECONCILE_COMPLETED";

    /**
     * Resource types whose subject/patient references need rewriting,
     * mapped to the search parameter used for patient linkage.
     */
    private static final Map<Class<? extends Resource>, String> RECONCILABLE_RESOURCES = new LinkedHashMap<>();

    static {
        RECONCILABLE_RESOURCES.put(Encounter.class, "subject");
        RECONCILABLE_RESOURCES.put(Condition.class, "subject");
        RECONCILABLE_RESOURCES.put(Observation.class, "subject");
        RECONCILABLE_RESOURCES.put(MedicationRequest.class, "subject");
        RECONCILABLE_RESOURCES.put(Immunization.class, "patient");
        RECONCILABLE_RESOURCES.put(DiagnosticReport.class, "subject");
        RECONCILABLE_RESOURCES.put(Procedure.class, "subject");
        RECONCILABLE_RESOURCES.put(CarePlan.class, "subject");
        RECONCILABLE_RESOURCES.put(AllergyIntolerance.class, "patient");
        RECONCILABLE_RESOURCES.put(ServiceRequest.class, "subject");
        RECONCILABLE_RESOURCES.put(DocumentReference.class, "subject");
    }

    private final DaoRegistry daoRegistry;
    private final ReconciliationMappingRepository mappingRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${butano.tenant.tag-system:https://impilo.gov.zw/tenant}")
    private String tenantTagSystem;

    @Value("${butano.reconciliation.batch-size:100}")
    private int batchSize;

    public ReconciliationService(DaoRegistry daoRegistry,
                                 ReconciliationMappingRepository mappingRepository,
                                 EventOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.daoRegistry = daoRegistry;
        this.mappingRepository = mappingRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Trigger asynchronous reconciliation for the given mapping.
     * Updates the mapping status from PENDING to IN_PROGRESS, then to
     * COMPLETED or FAILED depending on the outcome.
     *
     * @param mapping the reconciliation mapping entity with old/new CPID
     */
    @Async
    public void reconcile(ReconciliationMappingEntity mapping) {
        log.info("Starting reconciliation: O-CPID={} -> CPID={} tenant={}",
                mapping.getOldCpid(), mapping.getNewCpid(), mapping.getTenantId());

        mapping.setStatus(ReconciliationMappingEntity.STATUS_IN_PROGRESS);
        mappingRepository.save(mapping);

        try {
            String tenantId = mapping.getTenantId().toString();

            // 1. Resolve old Patient by CPID identifier
            String oldPatientRef = resolvePatientRef(mapping.getOldCpid(), tenantId);
            if (oldPatientRef == null) {
                log.warn("No Patient found for old CPID={}, marking as completed with 0 updates",
                        mapping.getOldCpid());
                completeMapping(mapping, 0);
                return;
            }

            // 2. Resolve new Patient by CPID identifier
            String newPatientRef = resolvePatientRef(mapping.getNewCpid(), tenantId);
            if (newPatientRef == null) {
                throw new IllegalStateException(
                        "No Patient found for new CPID: " + mapping.getNewCpid());
            }

            // 3. Rewrite references across all resource types
            int totalUpdated = 0;
            for (Map.Entry<Class<? extends Resource>, String> entry : RECONCILABLE_RESOURCES.entrySet()) {
                int updated = rewriteReferences(
                        entry.getKey(), entry.getValue(),
                        oldPatientRef, newPatientRef,
                        mapping.getOldCpid(), tenantId);
                totalUpdated += updated;
            }

            // 4. Mark completed
            completeMapping(mapping, totalUpdated);
            log.info("Reconciliation completed: O-CPID={} -> CPID={}, {} resources updated",
                    mapping.getOldCpid(), mapping.getNewCpid(), totalUpdated);

        } catch (Exception e) {
            log.error("Reconciliation failed for O-CPID={}: {}",
                    mapping.getOldCpid(), e.getMessage(), e);
            failMapping(mapping, e.getMessage());
        }
    }

    @Transactional
    protected void completeMapping(ReconciliationMappingEntity mapping, int resourcesUpdated) {
        mapping.setStatus(ReconciliationMappingEntity.STATUS_COMPLETED);
        mapping.setResourcesUpdated(resourcesUpdated);
        mapping.setCompletedAt(OffsetDateTime.now());
        mappingRepository.save(mapping);

        // Publish Kafka event via outbox
        publishOutboxEvent(mapping, resourcesUpdated);
    }

    @Transactional
    protected void failMapping(ReconciliationMappingEntity mapping, String errorMessage) {
        mapping.setStatus(ReconciliationMappingEntity.STATUS_FAILED);
        mapping.setCompletedAt(OffsetDateTime.now());
        mapping.setErrorMessage(truncate(errorMessage, 2000));
        mappingRepository.save(mapping);
    }

    private <T extends Resource> int rewriteReferences(
            Class<T> resourceClass, String patientParam,
            String oldPatientRef, String newPatientRef,
            String oldCpid, String tenantId) {

        IFhirResourceDao<T> dao = daoRegistry.getResourceDao(resourceClass);
        SearchParameterMap params = new SearchParameterMap();
        params.add(patientParam, new ReferenceParam(oldPatientRef));
        addTenantFilter(params, tenantId);
        params.setCount(batchSize);

        int totalUpdated = 0;
        boolean hasMore = true;

        while (hasMore) {
            IBundleProvider results = dao.search(params, (RequestDetails) null);
            if (results.isEmpty()) {
                break;
            }

            int count = results.sizeOrThrowNpe();
            List<IBaseResource> resources = results.getResources(0, Math.min(count, batchSize));

            for (IBaseResource baseResource : resources) {
                @SuppressWarnings("unchecked")
                T resource = (T) baseResource;
                rewriteSubjectReference(resource, newPatientRef, oldCpid);
                dao.update(resource, (RequestDetails) null);
                totalUpdated++;
            }

            hasMore = count > batchSize;
        }

        if (totalUpdated > 0) {
            log.debug("Rewrote {} {} resources from {} to {}",
                    totalUpdated, resourceClass.getSimpleName(), oldPatientRef, newPatientRef);
        }
        return totalUpdated;
    }

    private void rewriteSubjectReference(Resource resource, String newPatientRef, String oldCpid) {
        Reference newRef = new Reference(newPatientRef);

        // Rewrite subject/patient reference based on resource type
        if (resource instanceof Encounter enc) {
            enc.setSubject(newRef);
        } else if (resource instanceof Condition cond) {
            cond.setSubject(newRef);
        } else if (resource instanceof Observation obs) {
            obs.setSubject(newRef);
        } else if (resource instanceof MedicationRequest med) {
            med.setSubject(newRef);
        } else if (resource instanceof Immunization imm) {
            imm.setPatient(newRef);
        } else if (resource instanceof DiagnosticReport diag) {
            diag.setSubject(newRef);
        } else if (resource instanceof Procedure proc) {
            proc.setSubject(newRef);
        } else if (resource instanceof CarePlan plan) {
            plan.setSubject(newRef);
        } else if (resource instanceof AllergyIntolerance allergy) {
            allergy.setPatient(newRef);
        } else if (resource instanceof ServiceRequest sr) {
            sr.setSubject(newRef);
        } else if (resource instanceof DocumentReference docRef) {
            docRef.setSubject(newRef);
        }

        // Add provenance tag: reconciled-from:{oldCpid}
        resource.getMeta().addTag(new Coding()
                .setSystem(RECONCILED_TAG_SYSTEM)
                .setCode("reconciled-from:" + oldCpid)
                .setDisplay("Reconciled from O-CPID " + oldCpid));
    }

    private String resolvePatientRef(String cpid, String tenantId) {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);
        SearchParameterMap params = new SearchParameterMap();
        params.add("identifier", new TokenParam(CPID_SYSTEM, cpid));
        addTenantFilter(params, tenantId);
        params.setCount(1);

        IBundleProvider results = patientDao.search(params, (RequestDetails) null);
        List<IBaseResource> resources = results.getResources(0, 1);
        if (resources.isEmpty()) {
            return null;
        }
        Patient patient = (Patient) resources.get(0);
        return "Patient/" + patient.getIdElement().getIdPart();
    }

    private void publishOutboxEvent(ReconciliationMappingEntity mapping, int resourcesUpdated) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("mappingId", mapping.getId());
            payload.put("tenantId", mapping.getTenantId().toString());
            payload.put("oldCpid", mapping.getOldCpid());
            payload.put("newCpid", mapping.getNewCpid());
            payload.put("resourcesUpdated", resourcesUpdated);
            payload.put("completedAt", mapping.getCompletedAt().toString());

            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("RECONCILIATION");
            outbox.setAggregateId(mapping.getId().toString());
            outbox.setEventType(EVENT_TYPE_RECONCILE);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(mapping.getTenantId());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event for reconciliation {}: {}",
                    mapping.getId(), e.getMessage());
        }
    }

    private void addTenantFilter(SearchParameterMap params, String tenantId) {
        params.add("_tag", new TokenParam(tenantTagSystem, tenantId));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
