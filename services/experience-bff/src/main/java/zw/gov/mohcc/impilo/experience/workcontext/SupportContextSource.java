package zw.gov.mohcc.impilo.experience.workcontext;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Technical/operational support contexts from support-service's scoped
 * support-team assignments (A3) — never a universal role, always scoped to
 * a team whose facility/organisation/geographic reach is explicit.
 */
@Component
public class SupportContextSource {

    private final SupportServiceClient supportClient;
    private final WorkModeResolution modeResolution;

    public SupportContextSource(SupportServiceClient supportClient, WorkModeResolution modeResolution) {
        this.supportClient = supportClient;
        this.modeResolution = modeResolution;
    }

    public String systemName() {
        return "SUPPORT";
    }

    public WorkContextAdapterResult resolve(String actorHealthId) {
        if (actorHealthId == null || actorHealthId.isBlank()) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty(systemName()));
        }
        JsonNode assignments;
        try {
            assignments = supportClient.listSupportAssignmentsForPerson(actorHealthId);
        } catch (Exception e) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.degraded(systemName(), e.getMessage()));
        }
        if (assignments == null || !assignments.isArray() || assignments.isEmpty()) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty(systemName()));
        }

        List<ResolvedWorkContext> out = new ArrayList<>();
        boolean anyDegraded = false;
        for (JsonNode a : assignments) {
            if (!"ACTIVE".equalsIgnoreCase(text(a, "status"))) {
                continue;
            }
            String teamId = text(a, "teamId");
            JsonNode team = null;
            try {
                team = supportClient.getSupportTeam(teamId);
            } catch (Exception e) {
                anyDegraded = true;
            }
            out.add(toContext(a, team));
        }
        WorkContextSourceStatus status = out.isEmpty() ? WorkContextSourceStatus.empty(systemName())
                : anyDegraded ? WorkContextSourceStatus.degraded(systemName(), "team scope unavailable for one or more assignments")
                : WorkContextSourceStatus.live(systemName());
        return new WorkContextAdapterResult(out, status);
    }

    private ResolvedWorkContext toContext(JsonNode a, JsonNode team) {
        String assignmentId = text(a, "assignmentId");
        String supportRole = text(a, "supportRole");
        String facilityScopeRef = team != null ? text(team, "facilityScopeRef") : null;
        String organisationScope = team != null ? text(team, "organisationScope") : null;
        String teamName = team != null ? text(team, "name") : null;

        // A facility-scoped team is a FACILITY_SUPPORT context; anything else
        // (organisation/geographic/environment-scoped) is TECHNICAL_SUPPORT.
        String contextKind = "support";
        WorkModeResolution.Resolution modes = modeResolution.resolveForRoleTemplate(supportRole);
        List<String> availableModes = modes.availableModes();
        String defaultMode = modes.defaultMode();
        if (facilityScopeRef != null && availableModes.contains("FACILITY_SUPPORT")) {
            defaultMode = "FACILITY_SUPPORT";
        } else if (organisationScope != null && availableModes.contains("TECHNICAL_SUPPORT")) {
            defaultMode = "TECHNICAL_SUPPORT";
        }

        String label = (teamName != null ? teamName : "Support team") + (supportRole != null ? " (" + supportRole + ")" : "");

        return new ResolvedWorkContext(
                VashandiContextSource.contextIdFor(systemName(), assignmentId),
                contextKind, systemName(), assignmentId,
                facilityScopeRef, organisationScope, null, null,
                supportRole, List.of(), availableModes, defaultMode, modes.modeSource(),
                null, null, null, null, null,
                null, new ArrayList<>(), label, "other", 0);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
