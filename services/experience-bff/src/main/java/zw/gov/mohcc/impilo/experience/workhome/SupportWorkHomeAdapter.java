package zw.gov.mohcc.impilo.experience.workhome;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TECHNICAL_SUPPORT / FACILITY_SUPPORT — open support-service tickets. support-service's
 * ticket list has no team-scope filter yet (only status/priority/category/assigneeRef), so
 * this shows every open/in-progress ticket rather than only this actor's support team's scope
 * — a real, honestly-labelled gap (team-scoped filtering is deeper domain work, deferred), not
 * a silently wrong narrower result.
 */
@Component
public class SupportWorkHomeAdapter implements WorkHomeFamilyAdapter {

    private static final Logger log = LoggerFactory.getLogger(SupportWorkHomeAdapter.class);
    private static final String SECTION_ID = "support-tickets";
    private static final String TITLE = "Support tickets";

    private final SupportServiceClient supportClient;

    public SupportWorkHomeAdapter(SupportServiceClient supportClient) {
        this.supportClient = supportClient;
    }

    @Override
    public WorkHomeFamily family() {
        return WorkHomeFamily.SUPPORT;
    }

    @Override
    public List<FanoutTask<WorkHomeSection>> sectionTasks(ResolvedWorkContext context) {
        return List.of(FanoutTask.of(SECTION_ID, this::compose));
    }

    private WorkHomeSection compose() {
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            items.addAll(ticketsByStatus("OPEN"));
            items.addAll(ticketsByStatus("IN_PROGRESS"));
            return WorkHomeSection.ok(SECTION_ID, TITLE, items, Map.of("total", items.size()));
        } catch (Exception e) {
            log.debug("work-home support-tickets composition failed: {}", e.getMessage());
            return WorkHomeSection.degraded(SECTION_ID, TITLE, e.getMessage());
        }
    }

    private List<Map<String, Object>> ticketsByStatus(String status) {
        JsonNode tickets = supportClient.listTickets(status);
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode t : WorkHomeJson.asNodeList(tickets)) {
            out.add(toItem(t, status));
        }
        return out;
    }

    private Map<String, Object> toItem(JsonNode t, String status) {
        String id = WorkHomeJson.text(t, "id");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "ticket:" + id);
        item.put("kind", "SUPPORT_TICKET");
        item.put("source", "support");
        item.put("title", WorkHomeJson.text(t, "subject", WorkHomeJson.text(t, "title", "Support ticket")));
        item.put("description", WorkHomeJson.text(t, "description", "Awaiting action"));
        item.put("priority", WorkHomeJson.text(t, "priority", "MEDIUM"));
        item.put("status", status);
        item.put("assignee_id", WorkHomeJson.text(t, "assigneeRef"));
        item.put("created_at", WorkHomeJson.text(t, "createdAt"));
        item.put("href", "/support/tickets");
        return item;
    }
}
