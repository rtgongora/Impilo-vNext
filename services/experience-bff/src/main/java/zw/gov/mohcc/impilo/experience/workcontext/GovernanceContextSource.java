package zw.gov.mohcc.impilo.experience.workcontext;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Oversight / programme / virtual / organisation contexts from WGV
 * wgv_assignment rows — queried for BOTH subject types Vashandi-provider and
 * plain USER (today only PROVIDER is queried anywhere in the codebase; a
 * district manager, ICT support lead or programme officer with no Provider
 * ID would otherwise be invisible to the resolver).
 */
@Component
public class GovernanceContextSource {

    private final WorkforceGovernanceClient governanceClient;
    private final WorkModeResolution modeResolution;

    public GovernanceContextSource(WorkforceGovernanceClient governanceClient, WorkModeResolution modeResolution) {
        this.governanceClient = governanceClient;
        this.modeResolution = modeResolution;
    }

    public String systemName() {
        return "WGV";
    }

    public WorkContextAdapterResult resolve(String actorHealthId, String providerPublicId) {
        // Two independent subject-type fetches feed one merge — a person may hold BOTH
        // a provider assignment and a plain-user oversight assignment simultaneously,
        // so both are queried whenever the corresponding id is available.
        List<ResolvedWorkContext> out = new ArrayList<>();
        WorkContextSourceStatus.State worstState = null;

        if (providerPublicId != null && !providerPublicId.isBlank()) {
            FetchOutcome fo = fetchRaw("PROVIDER", providerPublicId);
            out.addAll(fo.contexts);
            worstState = worse(worstState, fo.state);
        }
        if (actorHealthId != null && !actorHealthId.isBlank()) {
            FetchOutcome fo = fetchRaw("USER", actorHealthId);
            out.addAll(fo.contexts);
            worstState = worse(worstState, fo.state);
        }

        if (worstState == null) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty(systemName()));
        }
        WorkContextSourceStatus status = switch (worstState) {
            case LIVE -> WorkContextSourceStatus.live(systemName());
            case EMPTY -> WorkContextSourceStatus.empty(systemName());
            case DEGRADED -> WorkContextSourceStatus.degraded(systemName(), "one or more subject-type queries failed");
        };
        return new WorkContextAdapterResult(out, status);
    }

    private record FetchOutcome(List<ResolvedWorkContext> contexts, WorkContextSourceStatus.State state) {}

    private FetchOutcome fetchRaw(String subjectType, String subjectId) {
        JsonNode data;
        try {
            data = governanceClient.searchAssignments(subjectType, subjectId, "ACTIVE");
        } catch (Exception e) {
            return new FetchOutcome(List.of(), WorkContextSourceStatus.State.DEGRADED);
        }
        if (data == null || !data.isArray() || data.isEmpty()) {
            return new FetchOutcome(List.of(), WorkContextSourceStatus.State.EMPTY);
        }
        List<ResolvedWorkContext> contexts = new ArrayList<>();
        for (JsonNode row : data) {
            ResolvedWorkContext ctx = toContext(row);
            if (ctx != null) contexts.add(ctx);
        }
        return new FetchOutcome(contexts, WorkContextSourceStatus.State.LIVE);
    }

    private static WorkContextSourceStatus.State worse(WorkContextSourceStatus.State a, WorkContextSourceStatus.State b) {
        if (a == null) return b;
        if (a == WorkContextSourceStatus.State.DEGRADED || b == WorkContextSourceStatus.State.DEGRADED) {
            return WorkContextSourceStatus.State.DEGRADED;
        }
        if (a == WorkContextSourceStatus.State.LIVE || b == WorkContextSourceStatus.State.LIVE) {
            return WorkContextSourceStatus.State.LIVE;
        }
        return WorkContextSourceStatus.State.EMPTY;
    }

    private ResolvedWorkContext toContext(JsonNode row) {
        String targetType = text(row, "targetType");
        String targetId = text(row, "targetId");
        String organisationId = text(row, "organisationId");
        String jurisdictionCode = text(row, "jurisdictionCode");
        String roleCode = text(row, "roleCode");
        String roleCategory = text(row, "roleCategory");
        String roleLevel = text(row, "roleLevel");
        String id = text(row, "id");
        boolean primary = row.path("primaryFlag").asBoolean(false);
        boolean secondary = row.path("secondaryFlag").asBoolean(false);

        String contextKind;
        String facilityId = null;
        String programmeId = null;
        switch (targetType == null ? "" : targetType) {
            case "FACILITY", "SITE" -> { contextKind = "facility"; facilityId = targetId; }
            case "ORGANISATION", "ORGANISATION_UNIT", "MULTI_SITE_GROUP", "SERVICE_NETWORK" -> contextKind = "organisation";
            case "JURISDICTION" -> contextKind = "jurisdiction";
            case "PROGRAM" -> { contextKind = "programme"; programmeId = targetId; }
            case "VIRTUAL_HUB" -> contextKind = "virtual";
            default -> { return null; }
        }

        WorkModeResolution.Resolution modes = modeResolution.resolve(roleCode, roleCategory, roleLevel, targetType);
        String groupHint = switch (contextKind) {
            case "jurisdiction", "organisation" -> "oversight";
            case "programme" -> "other";
            case "virtual" -> "virtual";
            default -> "regular";
        };

        String label = buildLabel(contextKind, targetId, organisationId, jurisdictionCode, roleCode);
        List<String> restrictions = new ArrayList<>();
        int rankBoost = primary ? 250 : (secondary ? 50 : 0);

        return new ResolvedWorkContext(
                VashandiContextSource.contextIdFor("WGV", id != null ? id : targetType + ":" + targetId),
                contextKind, "WGV", id,
                facilityId, organisationId, jurisdictionCode, programmeId,
                roleCode, List.of(), modes.availableModes(), modes.defaultMode(), modes.modeSource(),
                null, null, null, null, null,
                null, restrictions, label, groupHint, rankBoost);
    }

    private static String buildLabel(String contextKind, String targetId, String organisationId,
                                      String jurisdictionCode, String roleCode) {
        String anchor = switch (contextKind) {
            case "jurisdiction" -> jurisdictionCode != null ? jurisdictionCode : "Jurisdiction";
            case "programme" -> "Programme " + targetId;
            case "virtual" -> "Virtual " + targetId;
            default -> organisationId != null ? "Organisation " + organisationId : "Organisation";
        };
        return roleCode != null ? anchor + " (" + roleCode + ")" : anchor;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
