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
    private final ProgrammeRegistryLookup programmeRegistry;

    public GovernanceContextSource(WorkforceGovernanceClient governanceClient,
                                   WorkModeResolution modeResolution,
                                   ProgrammeRegistryLookup programmeRegistry) {
        this.governanceClient = governanceClient;
        this.modeResolution = modeResolution;
        this.programmeRegistry = programmeRegistry;
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

        List<String> restrictions = new ArrayList<>();
        String programmeName = null;

        // A programme appointment carries no programme lifecycle state of its own, so the
        // registry decides whether the appointment still means anything. Without this an
        // appointment to a closed programme is indistinguishable from an active duty.
        if ("programme".equals(contextKind)) {
            ProgrammeRegistryLookup.Outcome outcome = programmeRegistry.lookup(programmeId);
            if (outcome instanceof ProgrammeRegistryLookup.Outcome.Known known) {
                ProgrammeRegistryLookup.Programme programme = known.programme();
                programmeName = programme.name();
                if (programme.confersNoAuthority()) {
                    // The programme is over. Drop the context rather than show a duty that
                    // cannot be entered — the appointment row outliving the programme is a
                    // data-lifecycle artefact, not a workplace.
                    return null;
                }
                if (programme.isSuspended()) {
                    restrictions.add("PROGRAMME_SUSPENDED");
                } else if (!programme.isActive()) {
                    // An unrecognised status is not silently treated as ACTIVE.
                    restrictions.add("PROGRAMME_STATUS_UNRECOGNISED");
                }
            } else if (outcome instanceof ProgrammeRegistryLookup.Outcome.Unknown) {
                // The registry answered and does not know this programme — a dangling
                // reference of exactly the class Phase A5 quarantines.
                restrictions.add("PROGRAMME_UNRESOLVED");
            } else {
                // Registry unreachable. Deliberately NOT dropped: making an outage look
                // like "you have no programme duty" is the degradation-blindness the
                // resolver exists to prevent. It is marked unverified and rank-penalised;
                // the PDP, not this picker, is the enforcement boundary.
                restrictions.add("PROGRAMME_STATUS_UNVERIFIED");
            }
        }

        String label = buildLabel(contextKind, targetId, organisationId, jurisdictionCode, roleCode, programmeName);
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
                                      String jurisdictionCode, String roleCode, String programmeName) {
        String anchor = switch (contextKind) {
            case "jurisdiction" -> jurisdictionCode != null ? jurisdictionCode : "Jurisdiction";
            // The registry name where we have it — a picker row reading "Programme
            // 7f3a-…" identifies nothing to the person choosing it.
            case "programme" -> programmeName != null && !programmeName.isBlank()
                    ? programmeName
                    : "Programme " + targetId;
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
