package zw.gov.mohcc.impilo.experience.workhome;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PROGRAMME_MANAGEMENT — the programme registry record (WGV {@code wgv_programme}, A2). */
@Component
public class ProgrammeWorkHomeAdapter implements WorkHomeFamilyAdapter {

    private static final Logger log = LoggerFactory.getLogger(ProgrammeWorkHomeAdapter.class);
    private static final String SECTION_ID = "programme-profile";
    private static final String TITLE = "Programme";

    private final WorkforceGovernanceClient governanceClient;

    public ProgrammeWorkHomeAdapter(WorkforceGovernanceClient governanceClient) {
        this.governanceClient = governanceClient;
    }

    @Override
    public WorkHomeFamily family() {
        return WorkHomeFamily.PROGRAMME_MANAGEMENT;
    }

    @Override
    public List<FanoutTask<WorkHomeSection>> sectionTasks(ResolvedWorkContext context) {
        return List.of(FanoutTask.of(SECTION_ID, () -> compose(context)));
    }

    private WorkHomeSection compose(ResolvedWorkContext context) {
        String programmeId = context.programmeId();
        if (programmeId == null || programmeId.isBlank()) {
            return WorkHomeSection.empty(SECTION_ID, TITLE, "No programme anchor resolved for this context");
        }
        try {
            JsonNode programme = governanceClient.getJson("/v1/internal/governance/programmes/" + programmeId);
            if (programme == null || programme.isNull() || programme.isMissingNode()) {
                return WorkHomeSection.empty(SECTION_ID, TITLE, "Programme not found in the governance registry");
            }
            return WorkHomeSection.ok(SECTION_ID, TITLE, List.of(toItem(programme)), Map.of("total", 1));
        } catch (Exception e) {
            log.debug("work-home programme-profile composition failed: {}", e.getMessage());
            return WorkHomeSection.degraded(SECTION_ID, TITLE, e.getMessage());
        }
    }

    private Map<String, Object> toItem(JsonNode programme) {
        String id = WorkHomeJson.text(programme, "id", WorkHomeJson.text(programme, "programmeId"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "programme:" + id);
        item.put("kind", "PROGRAMME_PROFILE");
        item.put("source", "workforce-governance");
        item.put("title", WorkHomeJson.text(programme, "name", WorkHomeJson.text(programme, "code", "Programme")));
        item.put("description", "Type: " + WorkHomeJson.text(programme, "programmeType", "UNKNOWN"));
        item.put("status", WorkHomeJson.text(programme, "status", "UNKNOWN"));
        item.put("priority", "MEDIUM");
        item.put("href", "/programme/profile");
        return item;
    }
}
