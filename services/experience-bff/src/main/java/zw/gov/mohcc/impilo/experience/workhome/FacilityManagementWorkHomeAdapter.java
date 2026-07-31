package zw.gov.mohcc.impilo.experience.workhome;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.TusoFacilityClaimClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FACILITY_MANAGEMENT — facility status/completeness (TUSO) and pending facility-administrator
 * claims (TUSO facility_admin_appointment) requiring this manager's decision. Aggregate only,
 * no patient identifiers — the doctrine D4 purpose-specific-projection boundary for this family.
 */
@Component
public class FacilityManagementWorkHomeAdapter implements WorkHomeFamilyAdapter {

    private static final Logger log = LoggerFactory.getLogger(FacilityManagementWorkHomeAdapter.class);

    private final TusoServiceClient tusoClient;
    private final TusoFacilityClaimClient facilityClaimClient;

    public FacilityManagementWorkHomeAdapter(TusoServiceClient tusoClient, TusoFacilityClaimClient facilityClaimClient) {
        this.tusoClient = tusoClient;
        this.facilityClaimClient = facilityClaimClient;
    }

    @Override
    public WorkHomeFamily family() {
        return WorkHomeFamily.FACILITY_MANAGEMENT;
    }

    @Override
    public List<FanoutTask<WorkHomeSection>> sectionTasks(ResolvedWorkContext context) {
        return List.of(
                FanoutTask.of("facility-status", () -> composeStatus(context)),
                FanoutTask.of("facility-admin-claims", () -> composeAdminClaims(context)));
    }

    private WorkHomeSection composeStatus(ResolvedWorkContext context) {
        String facilityUuid = context.facilityId();
        if (facilityUuid == null || facilityUuid.isBlank()) {
            return WorkHomeSection.empty("facility-status", "Facility status", "No facility anchor resolved for this context");
        }
        try {
            JsonNode composite = tusoClient.getFacilityStatusComposite(facilityUuid);
            if (composite == null || composite.isNull()) {
                return WorkHomeSection.empty("facility-status", "Facility status", "No facility status record found");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            String status = WorkHomeJson.text(composite, "operatingStatus", WorkHomeJson.text(composite, "status", "UNKNOWN"));
            item.put("id", "facility-status:" + facilityUuid);
            item.put("kind", "FACILITY_STATUS");
            item.put("source", "tuso");
            item.put("title", "Facility status");
            item.put("description", WorkHomeJson.text(composite, "legitimacyStatus", status));
            item.put("status", status);
            item.put("priority", "MEDIUM");
            item.put("href", "/facility/" + facilityUuid + "/mode");
            return WorkHomeSection.ok("facility-status", "Facility status", List.of(item), Map.of("total", 1));
        } catch (Exception e) {
            log.debug("work-home facility-status composition failed: {}", e.getMessage());
            return WorkHomeSection.degraded("facility-status", "Facility status", e.getMessage());
        }
    }

    private WorkHomeSection composeAdminClaims(ResolvedWorkContext context) {
        String facilityUuid = context.facilityId();
        if (facilityUuid == null || facilityUuid.isBlank()) {
            return WorkHomeSection.empty("facility-admin-claims", "Administrator claims",
                    "No facility anchor resolved for this context");
        }
        try {
            JsonNode appointments = facilityClaimClient.appointments(facilityUuid);
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode a : WorkHomeJson.asNodeList(appointments)) {
                String state = WorkHomeJson.text(a, "approvalState", WorkHomeJson.text(a, "state"));
                if (!"PENDING".equalsIgnoreCase(state)) {
                    continue;
                }
                items.add(toClaimItem(a, facilityUuid));
            }
            return WorkHomeSection.ok("facility-admin-claims", "Administrator claims", items, Map.of("total", items.size()));
        } catch (Exception e) {
            log.debug("work-home facility-admin-claims composition failed: {}", e.getMessage());
            return WorkHomeSection.degraded("facility-admin-claims", "Administrator claims", e.getMessage());
        }
    }

    private Map<String, Object> toClaimItem(JsonNode a, String facilityUuid) {
        String id = WorkHomeJson.text(a, "id");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "admin-claim:" + (id != null ? id : facilityUuid));
        item.put("kind", "FACILITY_ADMIN_CLAIM");
        item.put("source", "tuso");
        item.put("title", "Facility administrator claim");
        item.put("description", "Role: " + WorkHomeJson.text(a, "role", "UNSPECIFIED") + " — awaiting your decision");
        // Not "PENDING": this claim awaits THIS manager's own approval, the reverse of the
        // usual "someone else must act" reading of that status — see WorkHomeBucketing.
        item.put("status", "PENDING_MY_APPROVAL");
        item.put("priority", "HIGH");
        item.put("person_health_id", WorkHomeJson.text(a, "personHealthId"));
        item.put("created_at", WorkHomeJson.text(a, "createdAt", Instant.now().toString()));
        item.put("href", "/facility/" + facilityUuid + "/mode");
        return item;
    }
}
