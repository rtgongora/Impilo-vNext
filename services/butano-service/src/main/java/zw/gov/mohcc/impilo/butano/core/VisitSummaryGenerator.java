package zw.gov.mohcc.impilo.butano.core;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Generates a visit summary Bundle for a given Encounter ID.
 *
 * Collects all FHIR resources associated with a specific encounter
 * (conditions, observations, diagnostic reports, medication requests,
 * procedures, service requests) and assembles them into a FHIR
 * Bundle of type COLLECTION. All resources are tenant-filtered.
 */
@Service
public class VisitSummaryGenerator {

    private static final Logger log = LoggerFactory.getLogger(VisitSummaryGenerator.class);

    private final DaoRegistry daoRegistry;

    @Value("${butano.tenant.tag-system:https://impilo.gov.zw/tenant}")
    private String tenantTagSystem;

    public VisitSummaryGenerator(DaoRegistry daoRegistry) {
        this.daoRegistry = daoRegistry;
    }

    /**
     * Generate a visit summary Bundle for the given encounter.
     *
     * @param encounterId the FHIR Encounter resource ID
     * @return a FHIR Bundle of type COLLECTION with all encounter-associated resources
     */
    public Bundle generate(String encounterId) {
        String tenantId = TrustContextHolder.require().tenantId().toString();
        log.info("Generating visit summary for Encounter/{} tenant={}", encounterId, tenantId);

        // 1. Resolve the Encounter
        Encounter encounter = resolveEncounter(encounterId, tenantId);
        String encounterRef = "Encounter/" + encounter.getIdElement().getIdPart();

        // 2. Gather all encounter-associated resources
        List<Condition> conditions = searchByEncounter(Condition.class, encounterRef, tenantId);
        List<Observation> observations = searchByEncounter(Observation.class, encounterRef, tenantId);
        List<DiagnosticReport> reports = searchByEncounter(DiagnosticReport.class, encounterRef, tenantId);
        List<MedicationRequest> medRequests = searchByEncounter(MedicationRequest.class, encounterRef, tenantId);
        List<Procedure> procedures = searchByEncounter(Procedure.class, encounterRef, tenantId);
        List<ServiceRequest> serviceRequests = searchByEncounter(ServiceRequest.class, encounterRef, tenantId);

        // 3. Build the Collection Bundle
        Bundle bundle = new Bundle();
        bundle.setId(UUID.randomUUID().toString());
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.setTimestamp(new Date());

        // Add Encounter itself as first entry
        addEntry(bundle, encounter);

        // Add all associated resources
        conditions.forEach(r -> addEntry(bundle, r));
        observations.forEach(r -> addEntry(bundle, r));
        reports.forEach(r -> addEntry(bundle, r));
        medRequests.forEach(r -> addEntry(bundle, r));
        procedures.forEach(r -> addEntry(bundle, r));
        serviceRequests.forEach(r -> addEntry(bundle, r));

        log.info("Visit summary generated for Encounter/{}: {} entries",
                encounterId, bundle.getEntry().size());
        return bundle;
    }

    private Encounter resolveEncounter(String encounterId, String tenantId) {
        IFhirResourceDao<Encounter> dao = daoRegistry.getResourceDao(Encounter.class);
        try {
            Encounter encounter = dao.read(new IdType("Encounter", encounterId), (RequestDetails) null);
            // Verify tenant tag
            boolean matchesTenant = encounter.getMeta().getTag().stream()
                    .anyMatch(tag -> tenantTagSystem.equals(tag.getSystem())
                            && tenantId.equals(tag.getCode()));
            if (!matchesTenant) {
                throw new ResourceNotFoundException(
                        "Encounter/" + encounterId + " not found for tenant " + tenantId);
            }
            return encounter;
        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException(
                    "Encounter/" + encounterId + " not found");
        }
    }

    private <T extends Resource> List<T> searchByEncounter(
            Class<T> resourceClass, String encounterRef, String tenantId) {
        IFhirResourceDao<T> dao = daoRegistry.getResourceDao(resourceClass);
        SearchParameterMap params = new SearchParameterMap();
        params.add("encounter", new ReferenceParam(encounterRef));
        addTenantFilter(params, tenantId);
        params.setCount(500);

        IBundleProvider results = dao.search(params, (RequestDetails) null);
        return extractAll(results);
    }

    private void addEntry(Bundle bundle, Resource resource) {
        bundle.addEntry()
                .setFullUrl(resource.fhirType() + "/" + resource.getIdElement().getIdPart())
                .setResource(resource);
    }

    private void addTenantFilter(SearchParameterMap params, String tenantId) {
        params.add("_tag", new TokenParam(tenantTagSystem, tenantId));
    }

    @SuppressWarnings("unchecked")
    private <T extends Resource> List<T> extractAll(IBundleProvider provider) {
        if (provider.isEmpty()) {
            return List.of();
        }
        int total = provider.sizeOrThrowNpe();
        List<IBaseResource> raw = provider.getResources(0, Math.min(total, 500));
        List<T> typed = new ArrayList<>(raw.size());
        for (IBaseResource r : raw) {
            typed.add((T) r);
        }
        return typed;
    }
}
