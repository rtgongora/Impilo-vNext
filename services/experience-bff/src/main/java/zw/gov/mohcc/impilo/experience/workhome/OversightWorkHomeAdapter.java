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

/**
 * JURISDICTION_OPERATIONS — the jurisdiction's own governed record (WGV {@code wgv_jurisdiction}).
 * WGV's assignment search has no jurisdiction-target filter today, so a real jurisdiction-wide
 * activity roll-up is deeper domain functionality this adapter honestly defers rather than
 * approximating with an unrelated query.
 */
@Component
public class OversightWorkHomeAdapter implements WorkHomeFamilyAdapter {

    private static final Logger log = LoggerFactory.getLogger(OversightWorkHomeAdapter.class);
    private static final String SECTION_ID = "jurisdiction-profile";
    private static final String TITLE = "Jurisdiction";

    private final WorkforceGovernanceClient governanceClient;

    public OversightWorkHomeAdapter(WorkforceGovernanceClient governanceClient) {
        this.governanceClient = governanceClient;
    }

    @Override
    public WorkHomeFamily family() {
        return WorkHomeFamily.OVERSIGHT;
    }

    @Override
    public List<FanoutTask<WorkHomeSection>> sectionTasks(ResolvedWorkContext context) {
        return List.of(FanoutTask.of(SECTION_ID, () -> compose(context)));
    }

    private WorkHomeSection compose(ResolvedWorkContext context) {
        String jurisdictionCode = context.jurisdictionCode();
        if (jurisdictionCode == null || jurisdictionCode.isBlank()) {
            return WorkHomeSection.empty(SECTION_ID, TITLE, "No jurisdiction anchor resolved for this context");
        }
        try {
            JsonNode jurisdictions = governanceClient.listJurisdictions();
            for (JsonNode j : WorkHomeJson.asNodeList(jurisdictions)) {
                if (!jurisdictionCode.equals(WorkHomeJson.text(j, "jurisdictionCode"))) {
                    continue;
                }
                return WorkHomeSection.ok(SECTION_ID, TITLE, List.of(toItem(j)), Map.of("total", 1));
            }
            return WorkHomeSection.empty(SECTION_ID, TITLE, "Jurisdiction not found in the governance registry");
        } catch (Exception e) {
            log.debug("work-home jurisdiction-profile composition failed: {}", e.getMessage());
            return WorkHomeSection.degraded(SECTION_ID, TITLE, e.getMessage());
        }
    }

    private Map<String, Object> toItem(JsonNode j) {
        String code = WorkHomeJson.text(j, "jurisdictionCode");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "jurisdiction:" + code);
        item.put("kind", "JURISDICTION_PROFILE");
        item.put("source", "workforce-governance");
        item.put("title", WorkHomeJson.text(j, "name", code));
        item.put("description", "Type: " + WorkHomeJson.text(j, "jurisdictionType", "UNKNOWN"));
        item.put("status", WorkHomeJson.text(j, "status", "UNKNOWN"));
        item.put("priority", "MEDIUM");
        item.put("href", "/public-health/oversight");
        return item;
    }
}
