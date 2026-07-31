package zw.gov.mohcc.impilo.experience.workhome;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REGULATORY_OPERATIONS / INSPECTION_COMPLIANCE — the regulator organisation's appointment
 * roster (org-registry). Registry/licensing/inspection workflow queues do not exist as a
 * separate downstream yet, so this shows the real appointment roster honestly rather than
 * inventing an inspection or licensing queue.
 */
@Component
public class RegulatoryWorkHomeAdapter implements WorkHomeFamilyAdapter {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryWorkHomeAdapter.class);
    private static final String SECTION_ID = "regulatory-appointments";
    private static final String TITLE = "Regulatory appointments";

    private final OrganizationRegistryServiceClient orgRegistryClient;

    public RegulatoryWorkHomeAdapter(OrganizationRegistryServiceClient orgRegistryClient) {
        this.orgRegistryClient = orgRegistryClient;
    }

    @Override
    public WorkHomeFamily family() {
        return WorkHomeFamily.REGULATORY;
    }

    @Override
    public List<FanoutTask<WorkHomeSection>> sectionTasks(ResolvedWorkContext context) {
        return List.of(FanoutTask.of(SECTION_ID, () -> compose(context)));
    }

    private WorkHomeSection compose(ResolvedWorkContext context) {
        String organisationId = context.organisationId();
        if (organisationId == null || organisationId.isBlank()) {
            return WorkHomeSection.empty(SECTION_ID, TITLE, "No regulator organisation anchor resolved for this context");
        }
        try {
            JsonNode appointments = orgRegistryClient.listAppointmentsForOrganization(organisationId);
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode a : WorkHomeJson.asNodeList(appointments)) {
                String status = WorkHomeJson.text(a, "status");
                if (status != null && !"ACTIVE".equalsIgnoreCase(status)) {
                    continue;
                }
                items.add(toItem(a));
            }
            return WorkHomeSection.ok(SECTION_ID, TITLE, items, Map.of("total", items.size()));
        } catch (Exception e) {
            log.debug("work-home regulatory-appointments composition failed: {}", e.getMessage());
            return WorkHomeSection.degraded(SECTION_ID, TITLE, e.getMessage());
        }
    }

    private Map<String, Object> toItem(JsonNode a) {
        String id = WorkHomeJson.text(a, "id");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "regulatory-appointment:" + id);
        item.put("kind", "REGULATORY_APPOINTMENT");
        item.put("source", "org-registry");
        item.put("title", WorkHomeJson.text(a, "roleCode", "Regulatory appointment"));
        item.put("description", "Jurisdiction: " + WorkHomeJson.text(a, "jurisdictionCode", "UNSPECIFIED"));
        item.put("status", WorkHomeJson.text(a, "status", "UNKNOWN"));
        item.put("priority", "MEDIUM");
        item.put("person_health_id", WorkHomeJson.text(a, "personHealthId"));
        item.put("href", "/work/regulatory");
        return item;
    }
}
