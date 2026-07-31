package zw.gov.mohcc.impilo.experience.workhome;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DEPARTMENT_MANAGEMENT — the canonical department profile from TUSO's {@code facility_department}
 * (A1). No department-level operational analytics service exists yet, so this adapter shows the
 * department's own governed attributes honestly rather than inventing queue/task counts a
 * downstream doesn't produce.
 */
@Component
public class DepartmentWorkHomeAdapter implements WorkHomeFamilyAdapter {

    private static final Logger log = LoggerFactory.getLogger(DepartmentWorkHomeAdapter.class);
    private static final String SECTION_ID = "department-profile";
    private static final String TITLE = "Department";

    private final TusoServiceClient tusoClient;

    public DepartmentWorkHomeAdapter(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    @Override
    public WorkHomeFamily family() {
        return WorkHomeFamily.DEPARTMENT_MANAGEMENT;
    }

    @Override
    public List<FanoutTask<WorkHomeSection>> sectionTasks(ResolvedWorkContext context) {
        return List.of(FanoutTask.of(SECTION_ID, () -> compose(context)));
    }

    private WorkHomeSection compose(ResolvedWorkContext context) {
        String facilityUuid = context.facilityId();
        if (facilityUuid == null || facilityUuid.isBlank()) {
            return WorkHomeSection.empty(SECTION_ID, TITLE, "No facility anchor resolved for this context");
        }
        try {
            JsonNode facility = tusoClient.getFacilityByUid(facilityUuid);
            String numericFacilityId = WorkHomeJson.text(facility, "id");
            if (numericFacilityId == null) {
                return WorkHomeSection.empty(SECTION_ID, TITLE, "Facility not found in the facility registry");
            }
            JsonNode departments = tusoClient.listFacilityDepartments(Long.parseLong(numericFacilityId));
            String departmentId = context.departmentId();
            for (JsonNode dept : WorkHomeJson.asNodeList(departments)) {
                String id = WorkHomeJson.text(dept, "id");
                if (departmentId != null && !departmentId.isBlank() && !departmentId.equals(id)) {
                    continue;
                }
                return WorkHomeSection.ok(SECTION_ID, TITLE, List.of(toItem(dept, facilityUuid)), Map.of("total", 1));
            }
            return WorkHomeSection.empty(SECTION_ID, TITLE, "Department not found in the facility registry");
        } catch (Exception e) {
            log.debug("work-home department-profile composition failed: {}", e.getMessage());
            return WorkHomeSection.degraded(SECTION_ID, TITLE, e.getMessage());
        }
    }

    private Map<String, Object> toItem(JsonNode dept, String facilityUuid) {
        String id = WorkHomeJson.text(dept, "id");
        String status = WorkHomeJson.text(dept, "operatingStatus", "UNKNOWN");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "department:" + id);
        item.put("kind", "DEPARTMENT_PROFILE");
        item.put("source", "tuso");
        item.put("title", WorkHomeJson.text(dept, "displayName", WorkHomeJson.text(dept, "officialName", "Department")));
        item.put("description", "Operating status: " + status);
        item.put("status", status);
        item.put("priority", "MEDIUM");
        item.put("department_type", WorkHomeJson.text(dept, "departmentType"));
        item.put("head_person_health_id", WorkHomeJson.text(dept, "headPersonHealthId"));
        item.put("href", "/facility/" + facilityUuid + "/departments");
        return item;
    }
}
