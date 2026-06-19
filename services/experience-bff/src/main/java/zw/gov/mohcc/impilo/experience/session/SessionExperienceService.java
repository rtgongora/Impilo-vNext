package zw.gov.mohcc.impilo.experience.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.vashandi.VashandiSessionContextResolver;

import java.util.*;

/**
 * Builds the Session Experience Contract from linked IDs, VARAPI professional truth,
 * and workforce governance assignments. Keycloak roles alone do not unlock Work.
 *
 * <p>Authority is sovereign-truth only: identity (VITO/VARAPI) plus governance
 * assignments (WGV). There is no preview superuser / Product Owner allowlist path —
 * every actor, including the Product Owner, resolves through the same seeded chain.</p>
 */
@Service
public class SessionExperienceService {

    private final VarapiServiceClient varapiClient;
    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final VashandiSessionContextResolver vashandiSessionContextResolver;
    private final ObjectMapper objectMapper;

    public SessionExperienceService(VarapiServiceClient varapiClient,
                                      WorkforceGovernanceClient workforceGovernanceClient,
                                      VashandiSessionContextResolver vashandiSessionContextResolver,
                                      ObjectMapper objectMapper) {
        this.varapiClient = varapiClient;
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.vashandiSessionContextResolver = vashandiSessionContextResolver;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> buildExperienceContract(String actorId,
                                                       String loginMethod,
                                                       String providerId,
                                                       boolean hasSelectedFacility,
                                                       String actorEmail) {
        Map<String, Object> linked = fetchLinkedIds(actorId);
        Map<String, Object> professionalTruth = fetchProfessionalTruth(actorId, linked);
        List<Map<String, Object>> assignments = fetchActiveAssignments(providerId != null ? providerId : stringVal(linked.get("providerId")));

        String resolvedProviderId = providerId != null ? providerId : stringVal(linked.get("providerId"));
        String providerStatus = stringVal(professionalTruth.get("providerWorkerStatus"));
        if (providerStatus == null) providerStatus = stringVal(linked.get("providerStatus"));

        boolean hasProfessional = isProfessionalEligible(resolvedProviderId, providerStatus);
        List<Map<String, Object>> activeAssignments = filterActiveAssignments(assignments);
        boolean hasWork = isWorkEligible(hasProfessional, providerStatus, professionalTruth, activeAssignments);
        Map<String, Object> organisationContext = resolveOrganisationContext(activeAssignments);
        String orgFriendlyBlock = organisationWorkBlocker(organisationContext, identityTypeFor(actorId, resolvedProviderId));
        if (orgFriendlyBlock != null) {
            hasWork = false;
        }
        Map<String, List<String>> managementWorkspaces = resolveManagementWorkspaces(organisationContext);

        String identityType = resolveIdentityType(actorId, resolvedProviderId);
        boolean personalVisible = actorId != null && !actorId.isBlank();
        boolean professionalVisible = hasProfessional;
        boolean workVisible = hasWork;

        String defaultTab = workVisible ? "work" : (professionalVisible && "provider_id".equals(loginMethod) ? "professional" : "personal");

        Map<String, Object> tabs = new LinkedHashMap<>(Map.of(
                "personal", tabDef(personalVisible, "My Life / My Health", "/home", "personal".equals(defaultTab), "health_id", null),
                "professional", tabDef(professionalVisible, "My Professional", "/professional", "professional".equals(defaultTab), "verified_provider_worker_id",
                        professionalVisible ? null : "No verified Provider/Worker ID"),
                "work", tabDef(workVisible, "Work", "/provider-workspace", "work".equals(defaultTab), "active_work_assignment",
                        workVisible ? null : "No active work assignment")
        ));

        String friendlyState = null;
        if (orgFriendlyBlock != null) friendlyState = orgFriendlyBlock;
        else if (hasProfessional && !hasWork) friendlyState = "no_active_work_assignment";
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
                "contractVersion", CONTRACT_VERSION,
                "opaPackages", List.of("impilo.tabs", "impilo.work", "impilo.professional", "impilo.registry", "impilo.marketplace", "impilo.organisation", "impilo.vashandi"),
                "enforcement", "bff_and_opa"
        ));
        if (!organisationContext.isEmpty()) {
            contract.put("organisation", organisationContext);
        }
        contract.put("visibleManagementWorkspaces", managementWorkspaces.getOrDefault("visible", List.of()));
        contract.put("blockedManagementWorkspaces", managementWorkspaces.getOrDefault("blocked", List.of()));
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
        vashandiSessionContextResolver.applyToContract(contract, actorId, resolvedProviderId);
        return contract;
    }

    public Map<String, Object> buildExperienceContract(String actorId,
                                                       String loginMethod,
                                                       String providerId,
                                                       boolean hasSelectedFacility) {
        return buildExperienceContract(actorId, loginMethod, providerId, hasSelectedFacility, null);
    }

    private Map<String, Object> fetchLinkedIds(String actorId) {
        try {
            JsonNode node = varapiClient.getProviderByHealthId(actorId);
            if (node == null || node.isNull()) return Map.of();
            Map<String, Object> m = new LinkedHashMap<>();
            String providerId = resolveProviderPublicId(node);
            if (providerId != null) m.put("providerId", providerId);
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
            if (s == null) {
                s = normalizeStatus(stringVal(a.get("status")));
            }
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
        return identityTypeFor(healthId, providerId);
    }

    private static String identityTypeFor(String healthId, String providerId) {
        if (providerId != null && !providerId.isBlank() && healthId != null) return "dual_citizen_provider";
        if (providerId != null && !providerId.isBlank()) return "provider_worker";
        return "citizen";
    }

    private Map<String, Object> resolveOrganisationContext(List<Map<String, Object>> assignments) {
        Map<String, Object> best = Map.of();
        int bestPriority = -1;
        for (Map<String, Object> assignment : assignments) {
            Map<String, Object> org = buildOrganisationContextFromAssignment(assignment);
            if (org.isEmpty()) {
                continue;
            }
            int priority = organisationContextPriority(org);
            if (priority > bestPriority) {
                best = org;
                bestPriority = priority;
            }
        }
        return best;
    }

    private Map<String, Object> buildOrganisationContextFromAssignment(Map<String, Object> assignment) {
        String organisationId = stringVal(assignment.get("organisationId"));
        if (organisationId == null || organisationId.isBlank()) {
            return Map.of();
        }
        Map<String, Object> org = new LinkedHashMap<>();
        org.put("organisationId", organisationId);
        putIfPresent(org, "organisationType", assignment.get("organisationType"));
        putIfPresent(org, "organisationName", assignment.get("organisationName"));
        putIfPresent(org, "organisationTrustTier", assignment.get("organisationTrustTier"));
        putIfPresent(org, "organisationLifecycleStatus", assignment.get("organisationLifecycleStatus"));
        putIfPresent(org, "organisationAccessEnvironment", assignment.get("organisationAccessEnvironment"));
        putIfPresent(org, "organisationCountry", assignment.get("organisationCountry"));
        putIfPresent(org, "userOrganisationRole", assignment.get("userOrganisationRole"));

        if (stringVal(org.get("organisationType")) == null) {
            enrichOrganisationFromWgv(org, organisationId);
        }

        String organisationType = normalizeOrgType(stringVal(org.get("organisationType")));
        if (organisationType != null) {
            org.put("organisationType", organisationType);
        }

        org.put("activeOrganisationAssignment", true);
        if (assignment.get("organisationDataScopes") instanceof List<?> scopes) {
            org.put("organisationDataScopes", scopes);
        }
        if (assignment.get("organisationApiScopes") instanceof List<?> scopes) {
            org.put("organisationApiScopes", scopes);
        }
        if (assignment.get("crossBorderAccessFlag") instanceof Boolean flag) {
            org.put("crossBorderAccessFlag", flag);
        }
        return org;
    }

    private void enrichOrganisationFromWgv(Map<String, Object> org, String organisationId) {
        try {
            JsonNode node = workforceGovernanceClient.getJson("/v1/internal/governance/organisations/" + organisationId);
            if (node == null || node.isNull()) {
                return;
            }
            putIfPresent(org, "organisationType", jsonText(node, "organisationType"));
            putIfPresent(org, "organisationName", jsonText(node, "name"));
            putIfPresent(org, "organisationLifecycleStatus", jsonText(node, "status"));
        } catch (Exception ignored) {
            // Downstream unavailable — keep partial org context.
        }
    }

    private static int organisationContextPriority(Map<String, Object> org) {
        String type = normalizeOrgType(stringVal(org.get("organisationType")));
        if ("sovereign_public_owner".equals(type)) return 100;
        if ("ministry_department".equals(type)) return 90;
        if ("health_professions_authority".equals(type)) return 80;
        if ("public_facility".equals(type)) return 50;
        return 10;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        String normalized = stringVal(value);
        if (normalized != null && !normalized.isBlank()) target.put(key, normalized);
    }

    private static String organisationWorkBlocker(Map<String, Object> organisation, String identityType) {
        if (organisation.isEmpty()) return null;
        String lifecycle = normalizeStatus(stringVal(organisation.get("organisationLifecycleStatus")));
        if ("suspended".equals(lifecycle)) return "organisation_suspended";
        if ("offboarded".equals(lifecycle) || "blacklisted".equals(lifecycle) || "revoked".equals(lifecycle)) {
            return "organisation_offboarded";
        }
        if ("expired".equals(lifecycle)) return "external_partner_access_expired";
        if (!"citizen".equals(identityType) && Boolean.FALSE.equals(organisation.get("activeOrganisationAssignment"))) {
            return "organisation_user_not_assigned";
        }
        if ("sandbox".equals(normalizeStatus(stringVal(organisation.get("organisationAccessEnvironment"))))) {
            return null;
        }
        return null;
    }

    private static Map<String, List<String>> resolveManagementWorkspaces(Map<String, Object> organisation) {
        String organisationType = normalizeOrgType(stringVal(organisation.get("organisationType")));
        if (organisationType == null || organisationType.isBlank()) {
            return Map.of("visible", List.of(), "blocked", List.of());
        }
        List<String> defaults = MANAGEMENT_WORKSPACE_DEFAULTS.getOrDefault(organisationType, List.of());
        Set<String> blocked = new LinkedHashSet<>();
        if (!SOVEREIGN_ORGANISATION_TYPES.contains(organisationType)) {
            blocked.addAll(SOVEREIGN_ONLY_WORKSPACES);
        }
        if (!MUNICIPAL_ORGANISATION_TYPES.contains(organisationType)) {
            blocked.addAll(MUNICIPAL_ONLY_WORKSPACES);
        }
        if (!PRIVATE_ORGANISATION_TYPES.contains(organisationType)) {
            blocked.addAll(PRIVATE_ONLY_WORKSPACES);
        }
        String lifecycle = normalizeStatus(stringVal(organisation.get("organisationLifecycleStatus")));
        if ("suspended".equals(lifecycle) || "offboarded".equals(lifecycle) || "blacklisted".equals(lifecycle)) {
            return Map.of("visible", List.of(), "blocked", defaults);
        }
        List<String> visible = defaults.stream().filter(w -> !blocked.contains(w)).toList();
        return Map.of("visible", visible, "blocked", new ArrayList<>(blocked));
    }

    private static final Set<String> SOVEREIGN_ORGANISATION_TYPES = Set.of("sovereign_public_owner", "ministry_department");
    private static final Set<String> MUNICIPAL_ORGANISATION_TYPES = Set.of(
            "city_health_department", "municipal_health_department", "local_authority_health_department");
    private static final Set<String> PRIVATE_ORGANISATION_TYPES = Set.of("private_hospital", "private_clinic");

    private static final List<String> SOVEREIGN_ONLY_WORKSPACES = List.of(
            "national_identity_governance", "national_organisation_registry", "national_system_operator_management",
            "mohcc_head_office_user_management", "national_platform_user_administration");
    private static final List<String> MUNICIPAL_ONLY_WORKSPACES = List.of(
            "municipal_user_management", "municipal_facility_staff_management", "municipal_public_health_team_management");
    private static final List<String> PRIVATE_ONLY_WORKSPACES = List.of(
            "private_facility_user_management", "private_clinician_assignment");

    /** Session Experience Contract version — kept in lockstep with the TS contract constant. */
    private static final String CONTRACT_VERSION = "1.3.0";

    private static final Map<String, List<String>> MANAGEMENT_WORKSPACE_DEFAULTS = Map.ofEntries(
            Map.entry("sovereign_public_owner", List.of("national_organisation_registry", "national_trust_console", "national_platform_user_administration")),
            Map.entry("ministry_department", List.of("mohcc_head_office_user_management", "national_policy_management")),
            Map.entry("provincial_medical_office", List.of("provincial_office_user_management", "public_facility_staff_management")),
            Map.entry("district_medical_office", List.of("district_office_user_management", "public_facility_staff_management")),
            Map.entry("public_facility", List.of("public_facility_staff_management", "facility_work_assignment")),
            Map.entry("city_health_department", List.of("municipal_organisation_management", "municipal_user_management", "municipal_facility_staff_management")),
            Map.entry("private_hospital", List.of("private_facility_user_management", "private_clinician_assignment", "private_non_clinical_staff_assignment")),
            Map.entry("private_clinic", List.of("private_facility_user_management", "private_clinician_assignment")),
            Map.entry("health_professions_authority", List.of("regulator_organisation_management", "council_user_management", "regulatory_audit")),
            Map.entry("software_vendor", List.of("marketplace_api_developer_assignment", "marketplace_sandbox_access_management")),
            Map.entry("insurer", List.of("payer_organisation_management", "payer_user_management", "claims_user_assignment")),
            Map.entry("foreign_research_partner", List.of("external_partner_user_management", "research_project_user_assignment", "deidentified_dataset_access"))
    );

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

    private static String normalizeOrgType(String organisationType) {
        return normalizeStatus(organisationType);
    }

    private static String jsonText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String resolveProviderPublicId(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.has("providerId") && !node.get("providerId").asText().isBlank()) {
            return node.get("providerId").asText();
        }
        if (node.has("providerPublicId") && !node.get("providerPublicId").asText().isBlank()) {
            return node.get("providerPublicId").asText();
        }
        return null;
    }

}
