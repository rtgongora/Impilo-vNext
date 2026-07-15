package zw.gov.mohcc.impilo.experience.vashandi;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * OPA-aligned precheck for Vashandi operational workforce actions.
 * Maps actions to {@code vashandi.*} workspaces from the trust catalogue.
 */
@Service
public class VashandiPolicyService {

    private static final String PACKAGE = "impilo.vashandi";
    private static final String VERSION = "1.0.0";

    private static final List<String> ALL_VASHANDI_WORKSPACES = List.of(
            "vashandi.dashboard",
            "vashandi.workforce_registry",
            "vashandi.my_roster",
            "vashandi.my_attendance",
            "vashandi.facility_staff",
            "vashandi.rosters",
            "vashandi.leave_availability",
            "vashandi.assignments",
            "vashandi.access_review",
            "vashandi.analytics",
            "vashandi.imports",
            "vashandi.hsc_postings",
            "vashandi.organisation_workforce",
            "vashandi.emergency_surge"
    );

    private static final Map<String, List<String>> ACTION_WORKSPACES = Map.ofEntries(
            Map.entry("WORKFORCE_PROFILE_LIST", List.of("vashandi.workforce_registry", "vashandi.dashboard", "vashandi.organisation_workforce")),
            Map.entry("WORKFORCE_PROFILE_VIEW", List.of("vashandi.workforce_registry", "vashandi.dashboard", "vashandi.organisation_workforce", "vashandi.my_roster")),
            Map.entry("WORKFORCE_PROFILE_RECONCILE", List.of("vashandi.workforce_registry", "vashandi.organisation_workforce")),
            Map.entry("ASSIGNMENT_LIST", List.of("vashandi.assignments", "vashandi.facility_staff", "vashandi.organisation_workforce")),
            Map.entry("ASSIGNMENT_CREATE", List.of("vashandi.assignments", "vashandi.facility_staff", "vashandi.organisation_workforce")),
            Map.entry("ASSIGNMENT_UPDATE", List.of("vashandi.assignments", "vashandi.facility_staff")),
            Map.entry("ASSIGNMENT_PRECHECK", List.of("vashandi.assignments", "vashandi.facility_staff")),
            Map.entry("ASSIGNMENT_APPROVE", List.of("vashandi.assignments", "vashandi.facility_staff", "vashandi.organisation_workforce")),
            Map.entry("ASSIGNMENT_ACTIVATE", List.of("vashandi.assignments", "vashandi.facility_staff")),
            Map.entry("ASSIGNMENT_SUSPEND", List.of("vashandi.assignments", "vashandi.facility_staff", "vashandi.access_review")),
            Map.entry("ASSIGNMENT_END", List.of("vashandi.assignments", "vashandi.facility_staff")),
            Map.entry("ROSTER_LIST", List.of("vashandi.rosters", "vashandi.my_roster", "vashandi.facility_staff")),
            Map.entry("ROSTER_CREATE", List.of("vashandi.rosters", "vashandi.facility_staff")),
            Map.entry("ROSTER_APPROVE", List.of("vashandi.rosters", "vashandi.facility_staff")),
            Map.entry("SHIFT_CREATE", List.of("vashandi.rosters", "vashandi.facility_staff")),
            Map.entry("SHIFT_UPDATE", List.of("vashandi.rosters", "vashandi.facility_staff")),
            Map.entry("VIRTUAL_POOL_READ", List.of("vashandi.rosters", "vashandi.facility_staff")),
            Map.entry("ATTENDANCE_CHECK_IN", List.of("vashandi.my_attendance", "vashandi.facility_staff")),
            Map.entry("ATTENDANCE_CHECK_OUT", List.of("vashandi.my_attendance", "vashandi.facility_staff")),
            Map.entry("ATTENDANCE_SUPERVISOR_CONFIRM", List.of("vashandi.facility_staff", "vashandi.my_attendance")),
            Map.entry("ATTENDANCE_LIST", List.of("vashandi.my_attendance", "vashandi.facility_staff", "vashandi.analytics")),
            Map.entry("LEAVE_LIST", List.of("vashandi.leave_availability", "vashandi.my_roster")),
            Map.entry("LEAVE_CREATE", List.of("vashandi.leave_availability", "vashandi.facility_staff")),
            Map.entry("LEAVE_UPDATE", List.of("vashandi.leave_availability", "vashandi.facility_staff")),
            Map.entry("AVAILABILITY_LIST", List.of("vashandi.leave_availability", "vashandi.my_roster")),
            Map.entry("ACCESS_RISK_LIST", List.of("vashandi.access_review", "vashandi.analytics")),
            Map.entry("ACCESS_RISK_SCAN", List.of("vashandi.access_review")),
            Map.entry("ACCESS_RISK_RESOLVE", List.of("vashandi.access_review")),
            Map.entry("ACCESS_RISK_REVOKE_ACCESS", List.of("vashandi.access_review")),
            Map.entry("ANALYTICS_STAFFING", List.of("vashandi.analytics", "vashandi.dashboard")),
            Map.entry("ANALYTICS_ROSTER_COVERAGE", List.of("vashandi.analytics", "vashandi.rosters")),
            Map.entry("ANALYTICS_ATTENDANCE", List.of("vashandi.analytics", "vashandi.facility_staff")),
            Map.entry("ANALYTICS_VACANCIES", List.of("vashandi.analytics", "vashandi.dashboard")),
            Map.entry("ANALYTICS_ACCESS_RISK", List.of("vashandi.analytics", "vashandi.access_review")),
            Map.entry("IMPORT_BRIDGE", List.of("vashandi.imports")),
            Map.entry("TRAINING_REQUIREMENT_LIST", List.of("vashandi.assignments", "vashandi.organisation_workforce", "vashandi.access_review")),
            Map.entry("TRAINING_REQUIREMENT_CREATE", List.of("vashandi.organisation_workforce", "vashandi.access_review")),
            Map.entry("TRAINING_REQUIREMENT_DEACTIVATE", List.of("vashandi.organisation_workforce", "vashandi.access_review")),
            Map.entry("HSC_POSTINGS_VIEW", List.of("vashandi.hsc_postings")),
            Map.entry("EMERGENCY_SURGE", List.of("vashandi.emergency_surge"))
    );

    public VashandiDtos.PolicyDecision evaluate(Map<String, Object> sessionContract, VashandiDtos.PrecheckRequest request) {
        List<String> warnings = new ArrayList<>();
        List<String> approvalsRequired = new ArrayList<>();
        String action = normalizeAction(request.requestedAction());
        String decisionId = UUID.randomUUID().toString();

        if (sessionContract == null || sessionContract.isEmpty()) {
            return denied(decisionId, warnings, approvalsRequired, "Session contract unavailable");
        }

        Set<String> visible = new LinkedHashSet<>(list(sessionContract.get("visibleVashandiWorkspaces")));
        visible.addAll(list(sessionContract.get("visibleWorkspaces")));
        visible.addAll(list(sessionContract.get("visibleManagementWorkspaces")));

        if (visible.isEmpty()) {
            return denied(decisionId, warnings, approvalsRequired,
                    "No Vashandi workspace authority in current session");
        }

        List<String> required = ACTION_WORKSPACES.getOrDefault(action, List.of());
        if (!required.isEmpty()) {
            boolean ok = required.stream().anyMatch(visible::contains);
            if (!ok) {
                return denied(decisionId, warnings, approvalsRequired,
                        "You do not have authority for this Vashandi action in your current scope.");
            }
        }

        String blocked = doctrineBlock(action, request, sessionContract, warnings);
        if (blocked != null) {
            return denied(decisionId, warnings, approvalsRequired, blocked);
        }

        if ("ASSIGNMENT_ACTIVATE".equals(action) || "SHIFT_CREATE".equals(action)) {
            String workforceStatus = str(sessionContract.get("currentWorkforceStatus"));
            if ("suspended".equalsIgnoreCase(normalize(workforceStatus))
                    || "dismissed".equalsIgnoreCase(normalize(workforceStatus))) {
                return denied(decisionId, warnings, approvalsRequired,
                        "Workforce status blocks activation for this worker.");
            }
        }

        return new VashandiDtos.PolicyDecision(true, PACKAGE, VERSION, decisionId, warnings, approvalsRequired);
    }

    public List<String> allWorkspaces() {
        return ALL_VASHANDI_WORKSPACES;
    }

    private static String doctrineBlock(
            String action,
            VashandiDtos.PrecheckRequest request,
            Map<String, Object> sessionContract,
            List<String> warnings) {
        @SuppressWarnings("unchecked")
        Map<String, Object> org = (Map<String, Object>) sessionContract.get("organisation");
        String orgType = org != null ? normalize(str(org.get("organisationType"))) : null;
        String providerStatus = normalize(str(sessionContract.get("providerWorkerStatus")));

        if ("health_professions_authority".equals(orgType)
                && Set.of("ASSIGNMENT_CREATE", "ASSIGNMENT_ACTIVATE", "ROSTER_CREATE", "SHIFT_CREATE").contains(action)) {
            return "Council users may reconcile workforce profiles but cannot manage facility assignments.";
        }
        if ("suspended".equals(providerStatus)
                && Set.of("ATTENDANCE_CHECK_IN", "ASSIGNMENT_ACTIVATE", "SHIFT_CREATE").contains(action)) {
            return "Provider/worker is suspended.";
        }
        if ("ASSIGNMENT_ACTIVATE".equals(action) && request.context() != null
                && "AUTO_ACTIVATE".equalsIgnoreCase(str(request.context().get("intent")))) {
            return "Assignments cannot be auto-activated without explicit approval.";
        }
        if ("ACCESS_RISK_REVOKE_ACCESS".equals(action)) {
            warnings.add("Access revocation is auditable and may require secondary approval.");
        }
        return null;
    }

    private static VashandiDtos.PolicyDecision denied(
            String decisionId,
            List<String> warnings,
            List<String> approvalsRequired,
            String reason) {
        warnings.add(reason);
        return new VashandiDtos.PolicyDecision(false, PACKAGE, VERSION, decisionId, warnings, approvalsRequired);
    }

    private static String normalizeAction(String action) {
        if (action == null) {
            return "";
        }
        return action.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static List<String> list(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out;
        }
        return List.of();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
