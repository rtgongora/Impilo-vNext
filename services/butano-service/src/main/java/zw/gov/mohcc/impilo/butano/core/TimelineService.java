package zw.gov.mohcc.impilo.butano.core;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Instant;
import java.util.*;

/**
 * Assembles a chronological timeline of clinical events for a given CPID.
 *
 * Queries multiple FHIR resource types (Encounter, Condition, Observation,
 * MedicationRequest, Immunization, DiagnosticReport, Procedure, CarePlan,
 * AllergyIntolerance, ServiceRequest) and merges them into a unified list
 * sorted by date (newest first). Supports filtering by resource type and
 * date range, with pagination.
 */
@Service
public class TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);

    private static final String CPID_SYSTEM = "https://impilo.gov.zw/cpid";

    /**
     * Resource types included in the timeline, mapped to the search parameter
     * name used for patient linkage.
     */
    private static final Map<Class<? extends Resource>, String> TIMELINE_RESOURCES = new LinkedHashMap<>();

    static {
        TIMELINE_RESOURCES.put(Encounter.class, "subject");
        TIMELINE_RESOURCES.put(Condition.class, "subject");
        TIMELINE_RESOURCES.put(Observation.class, "subject");
        TIMELINE_RESOURCES.put(MedicationRequest.class, "subject");
        TIMELINE_RESOURCES.put(Immunization.class, "patient");
        TIMELINE_RESOURCES.put(DiagnosticReport.class, "subject");
        TIMELINE_RESOURCES.put(Procedure.class, "subject");
        TIMELINE_RESOURCES.put(CarePlan.class, "subject");
        TIMELINE_RESOURCES.put(AllergyIntolerance.class, "patient");
        TIMELINE_RESOURCES.put(ServiceRequest.class, "subject");
    }

    private final DaoRegistry daoRegistry;

    @Value("${butano.tenant.tag-system:https://impilo.gov.zw/tenant}")
    private String tenantTagSystem;

    public TimelineService(DaoRegistry daoRegistry) {
        this.daoRegistry = daoRegistry;
    }

    /**
     * Build a timeline for the given CPID.
     *
     * @param cpid          the Clinical Patient Identifier
     * @param since         only include events on or after this date (nullable)
     * @param typeFilter    comma-separated resource type names to include (nullable = all)
     * @param page          zero-based page index
     * @param size          page size
     * @return list of TimelineItem sorted by date descending, paginated
     */
    public List<TimelineItem> buildTimeline(String cpid, String since,
                                            String typeFilter, int page, int size) {
        String tenantId = TrustContextHolder.require().tenantId().toString();
        log.debug("Building timeline for CPID={} tenant={} since={} types={}",
                cpid, tenantId, since, typeFilter);

        // Resolve Patient reference
        String patientRef = resolvePatientRef(cpid, tenantId);
        if (patientRef == null) {
            return List.of();
        }

        // Determine which resource types to query
        Set<String> allowedTypes = parseTypeFilter(typeFilter);

        // Query each resource type and collect timeline items
        List<TimelineItem> allItems = new ArrayList<>();

        for (Map.Entry<Class<? extends Resource>, String> entry : TIMELINE_RESOURCES.entrySet()) {
            Class<? extends Resource> resourceClass = entry.getKey();
            String patientParam = entry.getValue();

            if (!allowedTypes.isEmpty() && !allowedTypes.contains(resourceClass.getSimpleName())) {
                continue;
            }

            List<TimelineItem> items = queryResourceForTimeline(
                    resourceClass, patientParam, patientRef, tenantId, since);
            allItems.addAll(items);
        }

        // Sort by date descending (newest first)
        allItems.sort(Comparator.comparing(
                (TimelineItem item) -> item.date() != null ? item.date() : Instant.EPOCH).reversed());

        // Apply pagination
        int fromIndex = page * size;
        if (fromIndex >= allItems.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + size, allItems.size());
        return allItems.subList(fromIndex, toIndex);
    }

    /**
     * Return the total count of timeline items for pagination metadata.
     */
    public long countTimelineItems(String cpid, String since, String typeFilter) {
        String tenantId = TrustContextHolder.require().tenantId().toString();
        String patientRef = resolvePatientRef(cpid, tenantId);
        if (patientRef == null) {
            return 0;
        }

        Set<String> allowedTypes = parseTypeFilter(typeFilter);
        long total = 0;

        for (Map.Entry<Class<? extends Resource>, String> entry : TIMELINE_RESOURCES.entrySet()) {
            Class<? extends Resource> resourceClass = entry.getKey();
            String patientParam = entry.getValue();

            if (!allowedTypes.isEmpty() && !allowedTypes.contains(resourceClass.getSimpleName())) {
                continue;
            }

            total += countResources(resourceClass, patientParam, patientRef, tenantId, since);
        }
        return total;
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
            log.warn("No Patient found for CPID={} in tenant={}", cpid, tenantId);
            return null;
        }
        Patient patient = (Patient) resources.get(0);
        return "Patient/" + patient.getIdElement().getIdPart();
    }

    private <T extends Resource> List<TimelineItem> queryResourceForTimeline(
            Class<T> resourceClass, String patientParam,
            String patientRef, String tenantId, String since) {

        IFhirResourceDao<T> dao = daoRegistry.getResourceDao(resourceClass);
        SearchParameterMap params = new SearchParameterMap();
        params.add(patientParam, new ReferenceParam(patientRef));
        addTenantFilter(params, tenantId);

        if (since != null && !since.isBlank()) {
            params.add("date", new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, since));
        }

        params.setCount(500);

        IBundleProvider results = dao.search(params, (RequestDetails) null);
        if (results.isEmpty()) {
            return List.of();
        }

        int total = results.sizeOrThrowNpe();
        List<IBaseResource> raw = results.getResources(0, Math.min(total, 500));

        List<TimelineItem> items = new ArrayList<>(raw.size());
        for (IBaseResource r : raw) {
            Resource resource = (Resource) r;
            items.add(toTimelineItem(resource));
        }
        return items;
    }

    private <T extends Resource> long countResources(
            Class<T> resourceClass, String patientParam,
            String patientRef, String tenantId, String since) {

        IFhirResourceDao<T> dao = daoRegistry.getResourceDao(resourceClass);
        SearchParameterMap params = new SearchParameterMap();
        params.add(patientParam, new ReferenceParam(patientRef));
        addTenantFilter(params, tenantId);

        if (since != null && !since.isBlank()) {
            params.add("date", new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, since));
        }
        params.setCount(0);

        IBundleProvider results = dao.search(params, (RequestDetails) null);
        Integer size = results.size();
        return size != null ? size : 0;
    }

    private TimelineItem toTimelineItem(Resource resource) {
        String resourceType = resource.fhirType();
        String resourceId = resource.getIdElement().getIdPart();
        Instant date = extractDate(resource);
        String summary = extractSummary(resource);
        String encounterRef = extractEncounterRef(resource);

        return new TimelineItem(resourceType, resourceId, date, summary, encounterRef);
    }

    private Instant extractDate(Resource resource) {
        // Try common date fields based on resource type
        if (resource instanceof Encounter enc) {
            if (enc.hasPeriod() && enc.getPeriod().hasStart()) {
                return enc.getPeriod().getStart().toInstant();
            }
        } else if (resource instanceof Observation obs) {
            if (obs.hasEffectiveDateTimeType()) {
                return obs.getEffectiveDateTimeType().getValue().toInstant();
            }
        } else if (resource instanceof Condition cond) {
            if (cond.hasRecordedDate()) {
                return cond.getRecordedDate().toInstant();
            }
            if (cond.hasOnsetDateTimeType()) {
                return cond.getOnsetDateTimeType().getValue().toInstant();
            }
        } else if (resource instanceof MedicationRequest med) {
            if (med.hasAuthoredOn()) {
                return med.getAuthoredOn().toInstant();
            }
        } else if (resource instanceof Immunization imm) {
            if (imm.hasOccurrenceDateTimeType()) {
                return imm.getOccurrenceDateTimeType().getValue().toInstant();
            }
        } else if (resource instanceof DiagnosticReport diag) {
            if (diag.hasEffectiveDateTimeType()) {
                return diag.getEffectiveDateTimeType().getValue().toInstant();
            }
        } else if (resource instanceof Procedure proc) {
            if (proc.hasPerformedDateTimeType()) {
                return proc.getPerformedDateTimeType().getValue().toInstant();
            }
        } else if (resource instanceof CarePlan plan) {
            if (plan.hasPeriod() && plan.getPeriod().hasStart()) {
                return plan.getPeriod().getStart().toInstant();
            }
        } else if (resource instanceof AllergyIntolerance allergy) {
            if (allergy.hasRecordedDate()) {
                return allergy.getRecordedDate().toInstant();
            }
        } else if (resource instanceof ServiceRequest sr) {
            if (sr.hasAuthoredOn()) {
                return sr.getAuthoredOn().toInstant();
            }
        }

        // Fallback to meta.lastUpdated
        if (resource.hasMeta() && resource.getMeta().hasLastUpdated()) {
            return resource.getMeta().getLastUpdated().toInstant();
        }
        return null;
    }

    private String extractSummary(Resource resource) {
        if (resource instanceof Encounter enc) {
            StringBuilder sb = new StringBuilder("Encounter");
            if (enc.hasType() && !enc.getType().isEmpty()) {
                String display = enc.getTypeFirstRep().getText();
                if (display == null && enc.getTypeFirstRep().hasCoding()) {
                    display = enc.getTypeFirstRep().getCodingFirstRep().getDisplay();
                }
                if (display != null) sb.append(": ").append(display);
            }
            if (enc.hasStatus()) {
                sb.append(" (").append(enc.getStatus().toCode()).append(")");
            }
            return sb.toString();
        } else if (resource instanceof Condition cond) {
            if (cond.hasCode() && cond.getCode().hasText()) {
                return cond.getCode().getText();
            }
            if (cond.hasCode() && cond.getCode().hasCoding()) {
                return cond.getCode().getCodingFirstRep().getDisplay();
            }
            return "Condition";
        } else if (resource instanceof Observation obs) {
            if (obs.hasCode() && obs.getCode().hasText()) {
                return obs.getCode().getText();
            }
            if (obs.hasCode() && obs.getCode().hasCoding()) {
                return obs.getCode().getCodingFirstRep().getDisplay();
            }
            return "Observation";
        } else if (resource instanceof MedicationRequest med) {
            if (med.hasMedicationCodeableConcept() && med.getMedicationCodeableConcept().hasText()) {
                return med.getMedicationCodeableConcept().getText();
            }
            return "Medication Request";
        } else if (resource instanceof Immunization imm) {
            if (imm.hasVaccineCode() && imm.getVaccineCode().hasText()) {
                return imm.getVaccineCode().getText();
            }
            if (imm.hasVaccineCode() && imm.getVaccineCode().hasCoding()) {
                return imm.getVaccineCode().getCodingFirstRep().getDisplay();
            }
            return "Immunization";
        } else if (resource instanceof DiagnosticReport diag) {
            if (diag.hasCode() && diag.getCode().hasText()) {
                return diag.getCode().getText();
            }
            return "Diagnostic Report";
        } else if (resource instanceof Procedure proc) {
            if (proc.hasCode() && proc.getCode().hasText()) {
                return proc.getCode().getText();
            }
            return "Procedure";
        } else if (resource instanceof CarePlan plan) {
            if (plan.hasTitle()) {
                return plan.getTitle();
            }
            return "Care Plan";
        } else if (resource instanceof AllergyIntolerance allergy) {
            if (allergy.hasCode() && allergy.getCode().hasText()) {
                return allergy.getCode().getText();
            }
            return "Allergy/Intolerance";
        } else if (resource instanceof ServiceRequest sr) {
            if (sr.hasCode() && sr.getCode().hasText()) {
                return sr.getCode().getText();
            }
            return "Service Request";
        }
        return resource.fhirType();
    }

    private String extractEncounterRef(Resource resource) {
        if (resource instanceof Encounter enc) {
            return "Encounter/" + enc.getIdElement().getIdPart();
        } else if (resource instanceof Condition cond && cond.hasEncounter()) {
            return cond.getEncounter().getReference();
        } else if (resource instanceof Observation obs && obs.hasEncounter()) {
            return obs.getEncounter().getReference();
        } else if (resource instanceof MedicationRequest med && med.hasEncounter()) {
            return med.getEncounter().getReference();
        } else if (resource instanceof DiagnosticReport diag && diag.hasEncounter()) {
            return diag.getEncounter().getReference();
        } else if (resource instanceof Procedure proc && proc.hasEncounter()) {
            return proc.getEncounter().getReference();
        } else if (resource instanceof ServiceRequest sr && sr.hasEncounter()) {
            return sr.getEncounter().getReference();
        }
        return null;
    }

    private Set<String> parseTypeFilter(String typeFilter) {
        if (typeFilter == null || typeFilter.isBlank()) {
            return Set.of();
        }
        Set<String> types = new HashSet<>();
        for (String t : typeFilter.split(",")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                types.add(trimmed);
            }
        }
        return types;
    }

    private void addTenantFilter(SearchParameterMap params, String tenantId) {
        params.add("_tag", new TokenParam(tenantTagSystem, tenantId));
    }
}
