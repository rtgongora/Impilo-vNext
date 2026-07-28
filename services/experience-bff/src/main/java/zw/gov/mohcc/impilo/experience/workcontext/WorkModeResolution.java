package zw.gov.mohcc.impilo.experience.workcontext;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.WorkMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves a role_template_id's available WorkModes, preferring the seeded
 * catalog ({@code tshepo_authz.role_template_mode}, via
 * {@link TshepoAuthzServiceClient#workModesForRole}) and falling back to a
 * deterministic, NARROWING-ONLY derivation from WGV role metadata
 * (role_category/role_level/target_type) when no catalog entry exists —
 * never falling back to CLINICAL_CARE, since that would grant identified
 * clinical access to an unreviewed role.
 *
 * <p>On a TSHEPO-authz outage the resolution degrades to the same local
 * derivation, stamped {@code DERIVED_LOCAL} so the caller can disclose that
 * the mode list may be incomplete.</p>
 */
@Component
public class WorkModeResolution {

    private static final Logger log = LoggerFactory.getLogger(WorkModeResolution.class);

    private final TshepoAuthzServiceClient authzClient;

    public WorkModeResolution(TshepoAuthzServiceClient authzClient) {
        this.authzClient = authzClient;
    }

    public record Resolution(List<String> availableModes, String defaultMode, String modeSource) {
        static Resolution empty() {
            return new Resolution(List.of(), null, "DERIVED_LOCAL");
        }
    }

    /** Resolve modes for a role_template_id, e.g. a Vashandi duty template or org-registry appointment role. */
    public Resolution resolveForRoleTemplate(String roleTemplateId) {
        return resolve(roleTemplateId, null, null, null);
    }

    /**
     * Resolve modes for an assignment, using WGV's role_category/role_level/target_type
     * as the derivation fallback signal when the catalog has no seeded entry for
     * this role_template_id (open-ended WGV roles are not individually seeded).
     */
    public Resolution resolve(String roleTemplateId, String roleCategory, String roleLevel, String targetType) {
        if (roleTemplateId != null && !roleTemplateId.isBlank()) {
            try {
                JsonNode remote = authzClient.workModesForRole(roleTemplateId);
                List<String> modes = new ArrayList<>();
                String defaultMode = null;
                if (remote != null && remote.has("modes") && remote.get("modes").isArray()) {
                    for (JsonNode m : remote.get("modes")) {
                        String code = m.path("modeCode").asText(null);
                        if (code == null || code.isBlank()) continue;
                        modes.add(code);
                        if (m.path("default").asBoolean(false)) {
                            defaultMode = code;
                        }
                    }
                }
                if (!modes.isEmpty()) {
                    return new Resolution(List.copyOf(modes), defaultMode, "CATALOG");
                }
            } catch (Exception e) {
                log.warn("WorkMode catalog lookup failed for roleTemplateId={} (falling back to local derivation): {}",
                        roleTemplateId, e.getMessage());
            }
        }
        return deriveLocally(roleCategory, roleLevel, targetType);
    }

    /**
     * Narrowing-only local derivation from (role_category, role_level, target_type) —
     * mirrors the authz-side WorkModeCatalog derivation table so the two never
     * disagree in a way that widens access. Applied ONLY when the seeded
     * catalog has no entry; never overrides a seeded default.
     */
    private Resolution deriveLocally(String roleCategory, String roleLevel, String targetType) {
        if (roleCategory == null) {
            return Resolution.empty();
        }
        String category = roleCategory.toUpperCase();
        String level = roleLevel != null ? roleLevel.toUpperCase() : "";
        String target = targetType != null ? targetType.toUpperCase() : "";

        Optional<WorkMode> derived = switch (category) {
            case "CLINICAL" -> "FACILITY_LEVEL".equals(level) ? Optional.of(WorkMode.CLINICAL_CARE) : Optional.empty();
            case "TELEHEALTH" -> Optional.of(WorkMode.VIRTUAL_CARE);
            case "MANAGERIAL", "SUPERVISORY" -> {
                if ("JURISDICTIONAL".equals(level) || "NATIONAL".equals(level)) {
                    yield Optional.of(WorkMode.JURISDICTION_OPERATIONS);
                } else if ("FACILITY_LEVEL".equals(level) && "FACILITY".equals(target)) {
                    yield Optional.of(WorkMode.FACILITY_MANAGEMENT);
                }
                yield Optional.empty();
            }
            case "PROGRAMMATIC" -> "PROGRAM".equals(target) ? Optional.of(WorkMode.PROGRAMME_MANAGEMENT) : Optional.empty();
            case "REGULATORY", "QUALITY" -> "ORGANISATIONAL".equals(level) ? Optional.of(WorkMode.REGULATORY_OPERATIONS) : Optional.empty();
            case "SUPPORT" -> "ORGANISATION".equals(target)
                    ? Optional.of(WorkMode.TECHNICAL_SUPPORT)
                    : ("FACILITY".equals(target) ? Optional.of(WorkMode.FACILITY_SUPPORT) : Optional.empty());
            case "ADMINISTRATIVE", "LOGISTICS" -> "FACILITY".equals(target) ? Optional.of(WorkMode.FACILITY_SUPPORT) : Optional.empty();
            default -> Optional.empty();
        };

        if (derived.isEmpty()) {
            return Resolution.empty();
        }
        String mode = derived.get().name();
        return new Resolution(List.of(mode), mode, "DERIVED_LOCAL");
    }
}
