package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves which {@link zw.gov.mohcc.impilo.tshepo.contracts.enums.WorkMode}(s)
 * a role_template_id may hold, from {@code tshepo_authz.role_template_mode}
 * (V054) — the same heterogeneous key space {@link RoleTemplateCatalog}
 * already spans (Vashandi duty templates, org-registry appointment roles,
 * TUSO facility-admin roles).
 *
 * <p>Read-mostly: cached per {@code (tenant|roleTemplateId)} on first lookup,
 * misses cached as empty. A cache miss means "no seeded catalog entry" — the
 * caller (the resolver, Phase C) falls back to deterministic derivation from
 * WGV role metadata; it must never fabricate {@code CLINICAL_CARE} on a
 * miss.</p>
 */
@Service
public class WorkModeCatalog {

    private static final Logger log = LoggerFactory.getLogger(WorkModeCatalog.class);

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, List<RoleTemplateModeRow>> modeCache = new ConcurrentHashMap<>();
    private final Map<String, WorkModeDefinition> definitionCache = new ConcurrentHashMap<>();

    public WorkModeCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record RoleTemplateModeRow(String modeCode, boolean defaultMode, int rank) {}

    public record WorkModeDefinition(
            String modeCode, String displayLabel, String anchorKind,
            String clinicalDataAccess, String defaultPurposeOfUse, boolean requiresProvider) {}

    /** All modes (in seed rank order) a role_template_id may hold, or empty if none/on error. */
    public List<RoleTemplateModeRow> modesFor(UUID tenantId, String roleTemplateId) {
        if (tenantId == null || roleTemplateId == null || roleTemplateId.isBlank()) {
            return List.of();
        }
        String key = tenantId + "|" + roleTemplateId;
        return modeCache.computeIfAbsent(key, k -> loadModes(tenantId, roleTemplateId));
    }

    /** The default mode for a role_template_id, if the catalog defines one. */
    public Optional<String> defaultModeFor(UUID tenantId, String roleTemplateId) {
        return modesFor(tenantId, roleTemplateId).stream()
                .filter(RoleTemplateModeRow::defaultMode)
                .map(RoleTemplateModeRow::modeCode)
                .findFirst();
    }

    /** Mode semantics (anchor kind, clinical data access, default purpose) for a mode code. */
    public Optional<WorkModeDefinition> definition(UUID tenantId, String modeCode) {
        if (tenantId == null || modeCode == null || modeCode.isBlank()) {
            return Optional.empty();
        }
        String key = tenantId + "|" + modeCode;
        WorkModeDefinition def = definitionCache.computeIfAbsent(key, k -> loadDefinition(tenantId, modeCode));
        return Optional.ofNullable(def);
    }

    public List<WorkModeDefinition> allDefinitions(UUID tenantId) {
        try {
            return jdbcTemplate.query(
                    "SELECT mode_code, display_label, anchor_kind, clinical_data_access, "
                            + "default_purpose_of_use, requires_provider "
                            + "FROM tshepo_authz.work_mode_catalog WHERE tenant_id = ? AND active ORDER BY mode_code",
                    DEFINITION_ROW_MAPPER, tenantId);
        } catch (Exception e) {
            log.warn("work_mode_catalog list failed for tenant {} (treated as empty): {}", tenantId, e.getMessage());
            return List.of();
        }
    }

    private List<RoleTemplateModeRow> loadModes(UUID tenantId, String roleTemplateId) {
        try {
            List<RoleTemplateModeRow> rows = jdbcTemplate.query(
                    "SELECT mode_code, default_mode, rank FROM tshepo_authz.role_template_mode "
                            + "WHERE tenant_id = ? AND role_template_id = ? AND active ORDER BY rank ASC",
                    (rs, rowNum) -> new RoleTemplateModeRow(
                            rs.getString("mode_code"), rs.getBoolean("default_mode"), rs.getInt("rank")),
                    tenantId, roleTemplateId);
            return List.copyOf(rows);
        } catch (Exception e) {
            log.warn("role_template_mode lookup failed for {} (treated as empty): {}", roleTemplateId, e.getMessage());
            return List.of();
        }
    }

    private WorkModeDefinition loadDefinition(UUID tenantId, String modeCode) {
        try {
            List<WorkModeDefinition> rows = jdbcTemplate.query(
                    "SELECT mode_code, display_label, anchor_kind, clinical_data_access, "
                            + "default_purpose_of_use, requires_provider "
                            + "FROM tshepo_authz.work_mode_catalog WHERE tenant_id = ? AND mode_code = ? AND active",
                    DEFINITION_ROW_MAPPER, tenantId, modeCode);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            log.warn("work_mode_catalog lookup failed for {} (treated as unknown): {}", modeCode, e.getMessage());
            return null;
        }
    }

    private static final RowMapper<WorkModeDefinition> DEFINITION_ROW_MAPPER = (rs, rowNum) -> new WorkModeDefinition(
            rs.getString("mode_code"), rs.getString("display_label"), rs.getString("anchor_kind"),
            rs.getString("clinical_data_access"), rs.getString("default_purpose_of_use"), rs.getBoolean("requires_provider"));
}
