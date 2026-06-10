package zw.gov.mohcc.impilo.experience.admingovernance;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * OPA-aligned precheck for Administration & Governance actions.
 * Uses session contract management workspaces and action-specific doctrine rules.
 */
@Service
public class AdminGovernancePolicyService {

    private static final String PACKAGE = "impilo.organisation";
    private static final String VERSION = "1.6.0";

    private static final Map<String, List<String>> ACTION_WORKSPACES = Map.ofEntries(
            Map.entry("ONBOARD_CITIZEN", List.of("*")),
            Map.entry("ONBOARD_PROVIDER_WORKER", List.of("provider_worker_registry_admin", "council_user_management", "national_registry_governance")),
            Map.entry("ONBOARD_PUBLIC_SECTOR_WORKER", List.of("hsc_public_sector_worker_status_management", "public_facility_staff_management", "facility_work_assignment")),
            Map.entry("ONBOARD_HSC_USER", List.of("hsc_user_management", "hsc_scope_assignment")),
            Map.entry("ONBOARD_MUNICIPAL_USER", List.of("municipal_user_management", "municipal_organisation_management")),
            Map.entry("ONBOARD_PRIVATE_FACILITY_USER", List.of("private_facility_user_management", "mission_facility_user_management")),
            Map.entry("ONBOARD_REGULATOR_USER", List.of("council_user_management", "regulator_organisation_management")),
            Map.entry("ONBOARD_MADI_USER", List.of("blood_service_user_management", "blood_service_organisation_management")),
            Map.entry("ONBOARD_MARKETPLACE_USER", List.of("marketplace_user_management", "marketplace_sandbox_access_management")),
            Map.entry("ONBOARD_PAYER_USER", List.of("payer_user_management", "payer_organisation_management")),
            Map.entry("ONBOARD_TRAINING_USER", List.of("training_institution_management", "training_provider_user_management", "fundo_partner_user_management")),
            Map.entry("ONBOARD_EXTERNAL_PARTNER", List.of("external_partner_user_management", "research_partner_organisation_management")),
            Map.entry("ONBOARD_SYSTEM_ADMIN", List.of("national_platform_user_administration", "national_system_operator_management")),
            Map.entry("FACILITY_STAFF_ASSIGN", List.of("public_facility_staff_management", "facility_work_assignment", "municipal_facility_staff_management", "private_facility_user_management")),
            Map.entry("FACILITY_ASSIGNMENT_SUSPEND", List.of("public_facility_staff_management", "facility_work_assignment")),
            Map.entry("HSC_EMPLOYMENT_UPDATE", List.of("hsc_public_sector_worker_status_management", "hsc_user_management")),
            Map.entry("REGULATOR_PROVIDER_ACTION", List.of("council_user_management", "professional_registration_review", "licence_status_management")),
            Map.entry("REGULATOR_USER_CREATE", List.of("council_user_management", "regulator_organisation_management")),
            Map.entry("MADI_USER_CREATE", List.of("blood_service_user_management")),
            Map.entry("MARKETPLACE_USER_CREATE", List.of("marketplace_user_management")),
            Map.entry("MARKETPLACE_PRODUCTION_ACCESS", List.of("marketplace_production_access_management")),
            Map.entry("PAYER_USER_CREATE", List.of("payer_user_management")),
            Map.entry("PARTNER_USER_CREATE", List.of("external_partner_user_management")),
            Map.entry("ACCESS_REQUEST_DECIDE", List.of("*")),
            Map.entry("ORGANISATION_VERIFY", List.of("national_organisation_registry", "municipal_organisation_management", "private_facility_organisation_management")),
            Map.entry("ORGANISATION_CREATE", List.of("national_organisation_registry", "municipal_organisation_management", "private_facility_organisation_management")),
            Map.entry("ORGANISATION_SUSPEND", List.of("national_organisation_registry", "municipal_organisation_management", "private_facility_organisation_management")),
            Map.entry("ORGANISATION_OFFBOARD", List.of("national_organisation_registry", "municipal_organisation_management", "private_facility_organisation_management")),
            Map.entry("ORGANISATION_USER_CREATE", List.of("mohcc_head_office_user_management", "municipal_user_management", "private_facility_user_management", "marketplace_user_management")),
            Map.entry("ORGANISATION_USER_SUSPEND", List.of("mohcc_head_office_user_management", "municipal_user_management", "private_facility_user_management", "marketplace_user_management")),
            Map.entry("ORGANISATION_USER_OFFBOARD", List.of("mohcc_head_office_user_management", "municipal_user_management", "private_facility_user_management", "marketplace_user_management")),
            Map.entry("DATA_SHARING_APPROVAL", List.of("national_organisation_registry", "external_partner_user_management")),
            Map.entry("API_ACCESS_APPROVAL", List.of("marketplace_user_management", "marketplace_sandbox_access_management", "marketplace_production_access_management")),
            Map.entry("AUTHORISED_REPRESENTATIVE_INVITE", List.of("national_organisation_registry", "organisation_administrator", "hsc_user_management", "council_user_management")),
            Map.entry("BULK_IMPORT_ORGANISATION_USERS", List.of("organisation_data_uploader", "organisation_user_manager")),
            Map.entry("BULK_IMPORT_HSC_EMPLOYMENT_RECORDS", List.of("hsc_workforce_data_uploader", "hsc_public_sector_worker_status_management")),
            Map.entry("BULK_IMPORT_COUNCIL_PROFESSIONAL_REGISTER", List.of("council_register_data_uploader", "council_user_management")),
            Map.entry("BULK_IMPORT_FACILITY_STAFF_LIST", List.of("public_facility_staff_management", "private_facility_user_management"))
    );

    public AdminGovernanceDtos.PolicyDecision evaluate(
            Map<String, Object> sessionContract,
            AdminGovernanceDtos.PrecheckRequest request) {
        List<String> warnings = new ArrayList<>();
        List<String> approvalsRequired = new ArrayList<>();
        String action = normalizeAction(request.requestedAction());
        String decisionId = UUID.randomUUID().toString();

        if (sessionContract == null || sessionContract.isEmpty()) {
            return denied(decisionId, warnings, approvalsRequired, "Session contract unavailable");
        }

        @SuppressWarnings("unchecked")
        List<String> visibleMgmt = list(sessionContract.get("visibleManagementWorkspaces"));
        @SuppressWarnings("unchecked")
        List<String> visibleWs = list(sessionContract.get("visibleWorkspaces"));
        Set<String> visible = new HashSet<>();
        visible.addAll(visibleMgmt);
        visible.addAll(visibleWs);

        if (visible.isEmpty() && !hasGovernanceEntry(visibleMgmt, visibleWs)) {
            return denied(decisionId, warnings, approvalsRequired, "No management authority in current session");
        }

        List<String> required = ACTION_WORKSPACES.getOrDefault(action, List.of());
        if (!required.isEmpty() && !required.contains("*")) {
            boolean ok = required.stream().anyMatch(visible::contains);
            if (!ok) {
                return denied(decisionId, warnings, approvalsRequired,
                        "You do not have authority for this management action in your current scope.");
            }
        }

        String blocked = doctrineBlock(action, request, sessionContract, warnings, approvalsRequired);
        if (blocked != null) {
            return denied(decisionId, warnings, approvalsRequired, blocked);
        }

        if ("MARKETPLACE_PRODUCTION_ACCESS".equals(action)
                || "sandbox".equalsIgnoreCase(str(request.requestedEnvironment()))) {
            if ("production_full".equalsIgnoreCase(str(request.requestedEnvironment()))
                    || "production_limited".equalsIgnoreCase(str(request.requestedEnvironment()))) {
                if (!visible.contains("marketplace_production_access_management")) {
                    approvalsRequired.add("marketplace_production_access_officer");
                    warnings.add("Production access requires separate approval.");
                }
            }
        }

        if ("ONBOARD_EXTERNAL_PARTNER".equals(action)) {
            if (request.crossBorderFlag() != null && request.crossBorderFlag()
                    && !"aggregate_only".equalsIgnoreCase(str(request.requestedDataScope()))
                    && !"deidentified_only".equalsIgnoreCase(str(request.requestedDataScope()))) {
                approvalsRequired.add("cross_border_data_access_reviewer");
                warnings.add("Cross-border identified data access requires explicit approval.");
            }
            if (request.requestedDurationDays() == null || request.requestedDurationDays() <= 0) {
                return denied(decisionId, warnings, approvalsRequired,
                        "External partner access requires a time limit.");
            }
        }

        if (!approvalsRequired.isEmpty()) {
            warnings.add("This action requires approval before completion.");
        }

        return new AdminGovernanceDtos.PolicyDecision(
                true,
                PACKAGE,
                VERSION,
                decisionId,
                warnings,
                approvalsRequired
        );
    }

    private static String doctrineBlock(
            String action,
            AdminGovernanceDtos.PrecheckRequest request,
            Map<String, Object> sessionContract,
            List<String> warnings,
            List<String> approvalsRequired) {

        @SuppressWarnings("unchecked")
        Map<String, Object> org = (Map<String, Object>) sessionContract.get("organisation");
        String orgType = org != null ? str(org.get("organisationType")) : null;
        String env = str(request.requestedEnvironment());

        if ("FACILITY_STAFF_ASSIGN".equals(action) && "CREATE_PROVIDER_ID".equalsIgnoreCase(str(request.context() != null ? request.context().get("intent") : null))) {
            return "Facility managers cannot create Provider IDs here.";
        }
        if ("REGULATOR_USER_CREATE".equals(action) && "FACILITY_WORK_ASSIGN".equalsIgnoreCase(str(request.context() != null ? request.context().get("intent") : null))) {
            return "Council users cannot assign facility Work.";
        }
        if ("HSC_EMPLOYMENT_UPDATE".equals(action) && "ISSUE_COUNCIL_REGISTRATION".equalsIgnoreCase(str(request.context() != null ? request.context().get("intent") : null))) {
            return "HSC users cannot issue professional council registration.";
        }
        if (Set.of("MARKETPLACE_USER_CREATE", "PAYER_USER_CREATE", "PARTNER_USER_CREATE").contains(action)
                && "BROWSE_CLINICAL".equalsIgnoreCase(str(request.context() != null ? request.context().get("intent") : null))) {
            return "This actor type cannot browse clinical records by default.";
        }
        if ("MADI_USER_CREATE".equals(action) && "GENERAL_CLINICAL".equalsIgnoreCase(str(request.context() != null ? request.context().get("intent") : null))) {
            return "Madi roles do not grant general clinical access unless specifically assigned.";
        }
        if ("sandbox_only".equalsIgnoreCase(env) || "sandbox".equalsIgnoreCase(env)) {
            if ("production_full".equalsIgnoreCase(str(request.requestedEnvironment()))
                    || "production_limited".equalsIgnoreCase(str(request.requestedEnvironment()))) {
                return "This organisation is sandbox-only.";
            }
        }
        if ("municipal_health_department".equals(orgType) || "city_health_department".equals(orgType)) {
            if ("ONBOARD_SYSTEM_ADMIN".equals(action) || action.startsWith("MOHCC_")) {
                return "Municipal users do not inherit MoHCC national or provincial authority.";
            }
        }
        if ("private_hospital".equals(orgType) || "private_clinic".equals(orgType)) {
            if ("FACILITY_STAFF_ASSIGN".equals(action) && "public_facility".equalsIgnoreCase(str(request.context() != null ? request.context().get("targetFacilityType") : null))) {
                return "Private facility administrators cannot manage public facility staff.";
            }
        }
        if ("ONBOARD_PROVIDER_WORKER".equals(action) && request.roleTemplate() != null && request.roleTemplate().toLowerCase().contains("facility_work")) {
            warnings.add("Provider onboarding creates Professional access — Work requires a separate assignment.");
        }
        if ("AUTHORISED_REPRESENTATIVE_INVITE".equals(action)) {
            String actorId = str(request.targetSubjectId());
            String targetUserId = request.context() != null ? str(request.context().get("targetUserId")) : null;
            String intent = request.context() != null ? str(request.context().get("intent")) : null;
            if ("SELF_ESCALATE".equalsIgnoreCase(intent)
                    || (actorId != null && actorId.equals(targetUserId))) {
                return "Authorised representatives cannot grant themselves higher privileges.";
            }
            if (request.roleTemplate() != null && request.roleTemplate().contains("national_platform")) {
                return "Organisation representatives cannot assign national platform roles.";
            }
        }
        if (action.startsWith("BULK_IMPORT_") && request.context() != null
                && "AUTO_ACTIVATE".equalsIgnoreCase(str(request.context().get("intent")))) {
            return "Bulk import cannot auto-activate users from spreadsheet rows.";
        }
        return null;
    }

    private static AdminGovernanceDtos.PolicyDecision denied(
            String decisionId,
            List<String> warnings,
            List<String> approvalsRequired,
            String reason) {
        warnings.add(reason);
        return new AdminGovernanceDtos.PolicyDecision(
                false,
                PACKAGE,
                VERSION,
                decisionId,
                warnings,
                approvalsRequired
        );
    }

    private static boolean hasGovernanceEntry(List<String> mgmt, List<String> ws) {
        if (!mgmt.isEmpty()) return true;
        return ws.stream().anyMatch(w -> w.contains("management") || w.contains("governance"));
    }

    private static String normalizeAction(String action) {
        if (action == null) return "";
        return action.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static List<String> list(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) out.add(o.toString());
            }
            return out;
        }
        return List.of();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
