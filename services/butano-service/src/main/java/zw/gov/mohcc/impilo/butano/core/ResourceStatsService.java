package zw.gov.mohcc.impilo.butano.core;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.TokenParam;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns resource counts for key FHIR resource types stored in the SHR.
 *
 * Counts are scoped to the current tenant via the trust context.
 * This is used by administrative dashboards and health checks.
 */
@Service
public class ResourceStatsService {

    private static final Logger log = LoggerFactory.getLogger(ResourceStatsService.class);

    /**
     * The resource types to include in the stats report.
     */
    private static final List<Class<? extends Resource>> STAT_RESOURCE_TYPES = List.of(
            Patient.class,
            Encounter.class,
            Condition.class,
            Observation.class,
            MedicationRequest.class,
            Immunization.class,
            DiagnosticReport.class,
            Procedure.class,
            AllergyIntolerance.class,
            DocumentReference.class,
            ServiceRequest.class,
            CarePlan.class
    );

    private final DaoRegistry daoRegistry;

    @Value("${butano.tenant.tag-system:https://impilo.gov.zw/tenant}")
    private String tenantTagSystem;

    public ResourceStatsService(DaoRegistry daoRegistry) {
        this.daoRegistry = daoRegistry;
    }

    /**
     * Count resources for each key type, scoped to the current tenant.
     *
     * @return ordered map of { resourceType: count }
     */
    public Map<String, Long> getResourceCounts() {
        String tenantId = TrustContextHolder.require().tenantId().toString();
        log.debug("Collecting resource stats for tenant={}", tenantId);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Class<? extends Resource> resourceClass : STAT_RESOURCE_TYPES) {
            long count = countResources(resourceClass, tenantId);
            counts.put(resourceClass.getSimpleName(), count);
        }
        return counts;
    }

    private <T extends Resource> long countResources(Class<T> resourceClass, String tenantId) {
        try {
            IFhirResourceDao<T> dao = daoRegistry.getResourceDao(resourceClass);
            SearchParameterMap params = new SearchParameterMap();
            params.add("_tag", new TokenParam(tenantTagSystem, tenantId));
            params.setCount(0);

            IBundleProvider results = dao.search(params, (RequestDetails) null);
            Integer size = results.size();
            return size != null ? size : 0;
        } catch (Exception e) {
            log.warn("Failed to count {} resources for tenant={}: {}",
                    resourceClass.getSimpleName(), tenantId, e.getMessage());
            return -1;
        }
    }
}
