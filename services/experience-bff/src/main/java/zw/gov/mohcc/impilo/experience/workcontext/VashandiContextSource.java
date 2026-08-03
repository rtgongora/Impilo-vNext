package zw.gov.mohcc.impilo.experience.workcontext;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facility-clinical / department / virtual contexts from Vashandi's C2
 * work-context read-model — the SoR for physical duty assignments.
 */
@Component
public class VashandiContextSource {

    private final VashandiServiceClient vashandiClient;
    private final WorkModeResolution modeResolution;
    private final zw.gov.mohcc.impilo.experience.client.TusoServiceClient tusoClient;

    public VashandiContextSource(VashandiServiceClient vashandiClient,
                                 WorkModeResolution modeResolution,
                                 zw.gov.mohcc.impilo.experience.client.TusoServiceClient tusoClient) {
        this.vashandiClient = vashandiClient;
        this.modeResolution = modeResolution;
        this.tusoClient = tusoClient;
    }

    public String systemName() {
        return "VASHANDI";
    }

    public WorkContextAdapterResult resolve(String actorHealthId) {
        JsonNode response;
        try {
            response = vashandiClient.fetchWorkContext(actorHealthId);
        } catch (Exception e) {
            return new WorkContextAdapterResult(List.of(),
                    WorkContextSourceStatus.degraded(systemName(), e.getMessage()));
        }
        if (response == null || response.isNull() || response.isMissingNode()) {
            return new WorkContextAdapterResult(List.of(),
                    WorkContextSourceStatus.degraded(systemName(), "no response"));
        }
        boolean resolved = response.path("resolved").asBoolean(false);
        JsonNode assignments = response.path("activeAssignments");
        JsonNode checkIn = response.path("checkIn");
        if (!resolved || !assignments.isArray() || assignments.isEmpty()) {
            return new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty(systemName()));
        }

        List<ResolvedWorkContext> out = new ArrayList<>();
        for (JsonNode a : assignments) {
            out.add(toContext(a, checkIn));
        }
        return new WorkContextAdapterResult(out, WorkContextSourceStatus.live(systemName()));
    }

    private ResolvedWorkContext toContext(JsonNode a, JsonNode checkIn) {
        String assignmentId = text(a, "assignmentId");
        String facilityId = text(a, "facilityId");
        String organisationId = text(a, "organisationId");
        String departmentId = text(a, "departmentId");
        String wardId = text(a, "unitId");
        String programmeId = text(a, "programmeId");
        String workspaceId = text(a, "workspaceId");
        String roleTemplateId = text(a, "roleTemplateId");
        String scope = text(a, "scope");
        String virtualPoolId = text(a, "virtualPoolId");

        String contextKind = "VIRTUAL_POOL".equals(scope) ? "virtual"
                : "PROGRAMME".equals(scope) ? "programme"
                : "facility";

        WorkModeResolution.Resolution modes = modeResolution.resolveForRoleTemplate(roleTemplateId);

        List<String> restrictions = new ArrayList<>();
        String checkInState = checkIn != null ? text(checkIn, "state") : null;
        boolean checkedInHere = checkIn != null && facilityId != null
                && facilityId.equals(text(checkIn, "facilityId")) && "CHECKED_IN".equals(checkInState);

        Map<String, Object> shift = null;
        if (checkIn != null && checkedInHere) {
            shift = new LinkedHashMap<>();
            shift.put("shiftId", text(checkIn, "shiftId"));
            shift.put("checkInState", checkInState);
            shift.put("supervisorConfirmed", checkIn.path("supervisorConfirmed").asBoolean(false));
        }

        String sourceRecordId = assignmentId != null ? assignmentId : "unknown";
        String label = buildLabel(facilityId, departmentId, roleTemplateId, virtualPoolId);
        String groupHint = "virtual".equals(contextKind) ? "virtual" : "regular";

        return new ResolvedWorkContext(
                contextIdFor("VASHANDI", sourceRecordId),
                contextKind, "VASHANDI", sourceRecordId,
                facilityId, organisationId, null, programmeId,
                roleTemplateId, List.of(), modes.availableModes(), modes.defaultMode(), modes.modeSource(),
                departmentId, departmentId, wardId, null, workspaceId,
                shift, restrictions, label, groupHint, 0);
    }

    /**
     * Human label for a work context.
     *
     * <p>Previously this interpolated the raw facility UUID unconditionally — no name
     * resolution existed at all — so a clinician's workplace rendered as
     * "Facility f1000000-0000-0000-0000-000000000001". A person cannot tell one workplace
     * from another by UUID, and cannot tell a real one from a dangling reference.</p>
     *
     * <p>Resolves the name from TUSO, the facility master. When it cannot be resolved —
     * unknown id, unreadable tenant, TUSO down — say so in words rather than printing an
     * identifier that means nothing to the reader, and keep a SHORT id fragment so the row
     * is still reportable to support. The context itself always renders: a workplace that
     * cannot be named is still a workplace the person may hold.</p>
     */
    private String buildLabel(String facilityId, String departmentId, String roleTemplateId, String virtualPoolId) {
        StringBuilder sb = new StringBuilder();
        if (virtualPoolId != null) {
            sb.append("Virtual pool ").append(virtualPoolId);
        } else if (facilityId != null) {
            String name = tusoClient.facilityName(facilityId);
            if (name != null) {
                sb.append(name);
            } else {
                String shortId = facilityId.length() > 8 ? facilityId.substring(0, 8) : facilityId;
                sb.append("Unnamed facility (").append(shortId).append("…)");
            }
        } else {
            sb.append("Workplace");
        }
        if (departmentId != null) sb.append(" — ").append(departmentId);
        if (roleTemplateId != null) sb.append(" (").append(roleTemplateId).append(")");
        return sb.toString();
    }

    static String contextIdFor(String sourceSystem, String sourceRecordId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((sourceSystem + "|" + sourceRecordId).getBytes());
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16 && i < hash.length; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return sourceSystem + "-" + sourceRecordId + "-" + Instant.now().toEpochMilli();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
