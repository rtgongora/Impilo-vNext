package zw.gov.mohcc.impilo.experience.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.util.*;

/**
 * Builds the Session Experience Contract from linked IDs, VARAPI professional truth,
 * and workforce governance assignments. Keycloak roles alone do not unlock Work.
 */
@Service
public class SessionExperienceService {

    private final VarapiServiceClient varapiClient;
    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final ObjectMapper objectMapper;

    public SessionExperienceService(VarapiServiceClient varapiClient,
                                      WorkforceGovernanceClient workforceGovernanceClient,
                                      ObjectMapper objectMapper) {
        this.varapiClient = varapiClient;
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> buildExperienceContract(String actorId,
                                                       String loginMethod,
                                                       String providerId,
                                                       boolean hasSelectedFacility) {
        Map<String, Object> linked = fetchLinkedIds(actorId);
        Map<String, Object> professionalTruth = fetchProfessionalTruth(actorId, linked);
        List<Map<String, Object>> assignments = fetchActiveAssignments(providerId != null ? providerId : stringVal(linked.get("providerId")));

        String resolvedProviderId = providerId != null ? providerId : stringVal(linked.get("providerId"));
        String providerStatus = stringVal(professionalTruth.get("providerWorkerStatus"));
        if (providerStatus == null) providerStatus = stringVal(linked.get("providerStatus"));

        boolean hasProfessional = isProfessionalEligible(resolvedProviderId, providerStatus);
        List<Map<String, Object>> activeAssignments = filterActiveAssignments(assignments);
        boolean hasWork = isWorkEligible(hasProfessional, providerStatus, professionalTruth, activeAssignments);

        String identityType = resolveIdentityType(actorId, resolvedProviderId);
        boolean personalVisible = actorId != null && !actorId.isBlank();
        boolean professionalVisible = hasProfessional;
        boolean workVisible = hasWork;

        String defaultTab = workVisible ? "work" : (professionalVisible && "provider_id".equals(loginMethod) ? "professional" : "personal");

        Map<String, Object> tabs = Map.of(
                "personal", tabDef(personalVisible, "My Life / My Health", "/home", "personal".equals(defaultTab), "health_id", null),
                "professional", tabDef(professionalVisible, "My Professional", "/professional", "professional".equals(defaultTab), "verified_provider_worker_id",
                        professionalVisible ? null : "No verified Provider/Worker ID"),
                "work", tabDef(workVisible, "Work", "/provider-workspace", "work".equals(defaultTab), "active_work_assignment",
                        workVisible ? null : "No active work assignment")
        );

        String friendlyState = null;
        if (hasProfessional && !hasWork) friendlyState = "no_active_work_assignment";
        else if ("suspended".equalsIgnoreCase(providerStatus)) friendlyState = "provider_suspended";

        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("authenticated", true);
        contract.put("loginMethod", loginMethod != null ? loginMethod : "unknown");
        contract.put("identity", Map.of(
                "healthId", actorId,
                "providerWorkerId", resolvedProviderId != null ? resolvedProviderId : "",
                "identityType", identityType
        ));
        contract.put("tabs", tabs);
        contract.put("providerWorkerStatus", providerStatus != null ? providerStatus : "");
        contract.put("activeWorkAssignments", activeAssignments);
        contract.put("availableContexts", activeAssignments.stream().map(a -> stringVal(a.get("contextType"))).filter(Objects::nonNull).distinct().toList());
        contract.put("visibleWorkspaces", activeAssignments.stream().map(a -> stringVal(a.get("workspaceId"))).filter(Objects::nonNull).toList());
        contract.put("visibleActions", buildVisibleActions(personalVisible, professionalVisible, workVisible));
        contract.put("blockedActions", buildBlockedActions(workVisible, professionalVisible));
        contract.put("roleTemplates", activeAssignments.stream().map(a -> stringVal(a.get("roleTemplateId"))).filter(Objects::nonNull).toList());
        contract.put("policyMetadata", Map.of(
                "contractVersion", "1.0.0",
                "opaPackages", List.of("impilo.tabs", "impilo.work", "impilo.professional", "impilo.registry", "impilo.marketplace"),
                "enforcement", "bff_and_opa"
        ));
        contract.put("nompiloContext", Map.of(
                "activeTab", defaultTab,
                "suggestedPrompts", nompiloPrompts(defaultTab),
                "allowedActionDomains", List.of("nompilo." + defaultTab)
        ));
        contract.put("friendlyResolutionState", friendlyState != null ? friendlyState : "");
        contract.put("resolutionActions", resolutionActions(hasWork, hasProfessional));
        contract.put("requiresContextChooser", workVisible && activeAssignments.size() > 1 && !hasSelectedFacility);
        contract.put("facilityModeAvailable", workVisible && activeAssignments.stream().anyMatch(a -> a.get("facilityId") != null));
        contract.put("facilityModeActive", hasSelectedFacility);
        contract.put("defaultRoute", resolveDefaultRoute(defaultTab, friendlyState, activeAssignments, hasSelectedFacility));
        return contract;
    }

    private Map<String, Object> fetchLinkedIds(String actorId) {
        try {
            JsonNode node = varapiClient.getProviderByHealthId(actorId);
            if (node == null || node.isNull()) return Map.of();
            Map<String, Object> m = new LinkedHashMap<>();
            if (node.has("providerId")) m.put("providerId", node.get("providerId").asText());
            if (node.has("status")) m.put("providerStatus", node.get("status").asText());
            if (node.has("licenceValid")) m.put("licenceValid", node.get("licenceValid").asBoolean());
            return m;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> fetchProfessionalTruth(String actorId, Map<String, Object> linked) {
        Map<String, Object> truth = new LinkedHashMap<>();
        truth.put("providerWorkerId", linked.get("providerId"));
        truth.put("linkedHealthId", actorId);
        truth.put("providerWorkerStatus", normalizeStatus(stringVal(linked.get("providerStatus"))));
        return truth;
    }

    private List<Map<String, Object>> fetchActiveAssignments(String providerId) {
        if (providerId == null || providerId.isBlank()) return List.of();
        try {
            JsonNode data = workforceGovernanceClient.searchAssignments("PROVIDER", providerId, "ACTIVE");
            if (data == null || !data.isArray()) return List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode n : data) {
                Map<String, Object> row = objectMapper.convertValue(n, Map.class);
                row.putIfAbsent("assignmentStatus", "active");
                row.putIfAbsent("contextType", "facility_clinical");
                out.add(row);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<Map<String, Object>> filterActiveAssignments(List<Map<String, Object>> assignments) {
        return assignments.stream().filter(a -> {
            String s = normalizeStatus(stringVal(a.get("assignmentStatus")));
            return "active".equals(s) || "active_temporary".equals(s) || "active_restricted".equals(s);
        }).toList();
    }

    private static boolean isProfessionalEligible(String providerId, String status) {
        if (providerId == null || providerId.isBlank()) return false;
        if (status == null) return true;
        String s = normalizeStatus(status);
        return "verified".equals(s) || "active".equals(s) || "active_restricted".equals(s);
    }

    private static boolean isWorkEligible(boolean hasProfessional,
                                          String status,
                                          Map<String, Object> truth,
                                          List<Map<String, Object>> activeAssignments) {
        if (!hasProfessional || activeAssignments.isEmpty()) return false;
        if (status != null) {
            String s = normalizeStatus(status);
            if ("suspended".equals(s) || "expired".equals(s) || "revoked".equals(s)) return false;
        }
        return true;
    }

    private static String resolveIdentityType(String healthId, String providerId) {
        if (providerId != null && !providerId.isBlank() && healthId != null) return "dual_citizen_provider";
        if (providerId != null && !providerId.isBlank()) return "provider_worker";
        return "citizen";
    }

    private static Map<String, Object> tabDef(boolean visible, String label, String route, boolean isDefault, String requiredSource, String reason) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("visible", visible);
        t.put("label", label);
        t.put("route", route);
        t.put("default", isDefault);
        t.put("requiredSource", requiredSource);
        if (reason != null) t.put("reason", reason);
        return t;
    }

    private static List<String> buildVisibleActions(boolean personal, boolean professional, boolean work) {
        List<String> actions = new ArrayList<>();
        if (personal) actions.add("personal.profile.view");
        if (professional) actions.add("professional.profile.view");
        if (work) actions.add("work.context.enter");
        return actions;
    }

    private static List<String> buildBlockedActions(boolean work, boolean professional) {
        List<String> blocked = new ArrayList<>();
        if (!work) blocked.addAll(List.of("work.context.enter", "facility.staff.assign", "clinical.encounter.create"));
        if (!professional) blocked.add("professional.profile.view");
        return blocked;
    }

    private static List<String> nompiloPrompts(String tab) {
        return switch (tab) {
            case "work" -> List.of("Open today's queue", "Find client by Health ID", "Start consultation");
            case "professional" -> List.of("Show my professional licence status", "Open Fundo courses");
            default -> List.of("Book me an appointment", "Show my prescriptions", "Show my health card");
        };
    }

    private static List<String> resolutionActions(boolean hasWork, boolean hasProfessional) {
        if (hasWork) return List.of();
        if (hasProfessional) return List.of("View Professional Profile", "Request Work Assignment", "Switch to Personal");
        return List.of("View My Health");
    }

    private static String resolveDefaultRoute(String defaultTab, String friendlyState, List<Map<String, Object>> assignments, boolean hasFacility) {
        if ("provider_suspended".equals(friendlyState)) return "/provider/status";
        if ("work".equals(defaultTab)) {
            if (assignments.size() > 1 && !hasFacility) return "/auth/context-chooser";
            if (assignments.size() == 1 && !hasFacility) return "/facility";
            return "/provider-workspace";
        }
        if ("professional".equals(defaultTab)) return "/professional";
        return "/home";
    }

    private static String stringVal(Object o) {
        return o == null ? null : o.toString();
    }

    private static String normalizeStatus(String status) {
        if (status == null) return null;
        return status.trim().toLowerCase().replace(' ', '_').replace('-', '_');
    }
}
