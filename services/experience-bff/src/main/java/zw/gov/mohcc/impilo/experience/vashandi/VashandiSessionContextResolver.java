package zw.gov.mohcc.impilo.experience.vashandi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.util.*;

/**
 * Resolves Vashandi session context from upstream service with honest degraded states.
 */
@Component
public class VashandiSessionContextResolver {

    private final VashandiServiceClient vashandiClient;
    private final VashandiPolicyService policyService;
    private final ObjectMapper objectMapper;

    public VashandiSessionContextResolver(VashandiServiceClient vashandiClient,
                                          VashandiPolicyService policyService,
                                          ObjectMapper objectMapper) {
        this.vashandiClient = vashandiClient;
        this.policyService = policyService;
        this.objectMapper = objectMapper;
    }

    public VashandiDtos.SessionWorkforceContext resolve(String healthId,
                                                        String providerWorkerId,
                                                        Map<String, Object> sessionContract) {
        JsonNode upstream = vashandiClient.fetchSessionContext(healthId, providerWorkerId);
        if (upstream == null || upstream.isNull()) {
            return emptySessionContext(sessionContract, VashandiDtos.IntegrationStatus.UPSTREAM_UNAVAILABLE);
        }
        return mapSessionContext(upstream, sessionContract, VashandiDtos.IntegrationStatus.LIVE);
    }

    public Map<String, Object> applyToContract(Map<String, Object> contract,
                                               String healthId,
                                               String providerWorkerId) {
        VashandiDtos.SessionWorkforceContext ctx = resolve(healthId, providerWorkerId, contract);
        contract.put("vashandiWorkforceProfileId", ctx.vashandiWorkforceProfileId() != null ? ctx.vashandiWorkforceProfileId() : "");
        contract.put("currentWorkforceStatus", ctx.currentWorkforceStatus() != null ? ctx.currentWorkforceStatus() : "");
        contract.put("currentAssignments", ctx.currentAssignments());
        contract.put("activeRosteredShifts", ctx.activeRosteredShifts());
        contract.put("attendanceStatus", ctx.attendanceStatus() != null ? ctx.attendanceStatus() : "");
        contract.put("leaveAvailabilityStatus", ctx.leaveAvailabilityStatus() != null ? ctx.leaveAvailabilityStatus() : "");
        contract.put("workforceAccessRisks", ctx.workforceAccessRisks());
        contract.put("visibleVashandiWorkspaces", ctx.visibleVashandiWorkspaces());
        contract.put("blockedVashandiWorkspaces", ctx.blockedVashandiWorkspaces());
        contract.put("vashandiFriendlyResolutionState", ctx.vashandiFriendlyResolutionState() != null ? ctx.vashandiFriendlyResolutionState() : "");
        contract.put("vashandiIntegrationStatus", ctx.integrationStatus().name());
        return contract;
    }

    private VashandiDtos.SessionWorkforceContext emptySessionContext(
            Map<String, Object> contract,
            VashandiDtos.IntegrationStatus status) {
        List<String> visible = resolveWorkspacesFromContract(contract);
        List<String> blocked = policyService.allWorkspaces().stream().filter(w -> !visible.contains(w)).toList();
        return new VashandiDtos.SessionWorkforceContext(
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                visible,
                blocked,
                status == VashandiDtos.IntegrationStatus.UPSTREAM_UNAVAILABLE ? "vashandi_upstream_unavailable" : null,
                status
        );
    }

    private VashandiDtos.SessionWorkforceContext mapSessionContext(
            JsonNode node,
            Map<String, Object> contract,
            VashandiDtos.IntegrationStatus status) {
        List<Map<String, Object>> assignments = toMapList(node.path("currentAssignments"));
        List<Map<String, Object>> shifts = toMapList(node.path("activeRosteredShifts"));
        List<Map<String, Object>> risks = toMapList(node.path("workforceAccessRisks"));
        List<String> visible = toStringList(node.path("visibleVashandiWorkspaces"));
        if (visible.isEmpty()) {
            visible = resolveWorkspacesFromContract(contract);
        }
        final List<String> visibleWorkspaces = List.copyOf(visible);
        List<String> blocked = toStringList(node.path("blockedVashandiWorkspaces"));
        if (blocked.isEmpty()) {
            blocked = policyService.allWorkspaces().stream().filter(w -> !visibleWorkspaces.contains(w)).toList();
        }
        return new VashandiDtos.SessionWorkforceContext(
                text(node, "vashandiWorkforceProfileId"),
                text(node, "currentWorkforceStatus"),
                assignments,
                shifts,
                text(node, "attendanceStatus"),
                text(node, "leaveAvailabilityStatus"),
                risks,
                visibleWorkspaces,
                blocked,
                text(node, "vashandiFriendlyResolutionState"),
                status
        );
    }

    @SuppressWarnings("unchecked")
    List<String> resolveWorkspacesFromContract(Map<String, Object> contract) {
        List<String> roleTemplates = (List<String>) contract.getOrDefault("roleTemplates", List.of());
        Map<String, Object> org = (Map<String, Object>) contract.get("organisation");
        String orgType = org != null ? str(org.get("organisationType")) : null;
        boolean workVisible = contract.get("tabs") instanceof Map<?, ?> tabs
                && tabs.get("work") instanceof Map<?, ?> workTab
                && Boolean.TRUE.equals(((Map<String, Object>) workTab).get("visible"));
        List<String> visible = new ArrayList<>();
        if (workVisible) {
            visible.addAll(List.of(
                    "vashandi.dashboard",
                    "vashandi.my_roster",
                    "vashandi.my_attendance",
                    "vashandi.leave_availability"));
        }
        for (String role : roleTemplates) {
            String normalized = role == null ? "" : role.toLowerCase(Locale.ROOT);
            if (normalized.contains("facility_workforce_manager") || normalized.contains("roster_manager")) {
                visible.addAll(List.of("vashandi.facility_staff", "vashandi.rosters", "vashandi.assignments", "vashandi.analytics"));
            }
            if (normalized.contains("access_reviewer")) {
                visible.add("vashandi.access_review");
            }
            if (normalized.contains("hsc_workforce")) {
                visible.add("vashandi.hsc_postings");
            }
            if (normalized.contains("national_workforce")) {
                visible.add("vashandi.workforce_registry");
            }
            if (normalized.contains("organisation_workforce")) {
                visible.add("vashandi.organisation_workforce");
            }
            if (normalized.contains("emergency_surge")) {
                visible.add("vashandi.emergency_surge");
            }
        }
        if ("health_service_commission".equals(orgType)) {
            visible.add("vashandi.hsc_postings");
        }
        if ("public_facility".equals(orgType)) {
            visible.addAll(List.of("vashandi.facility_staff", "vashandi.rosters"));
        }
        return visible.stream().distinct().toList();
    }

    private List<Map<String, Object>> toMapList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        node.forEach(item -> out.add(objectMapper.convertValue(item, Map.class)));
        return out;
    }

    private List<String> toStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        node.forEach(item -> out.add(item.asText()));
        return out;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
