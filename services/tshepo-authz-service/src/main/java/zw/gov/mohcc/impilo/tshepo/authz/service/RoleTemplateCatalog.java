package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves an opaque Vashandi {@code roleTemplateId} to its canonical policy role(s)
 * from {@code tshepo_authz.role_template_catalog} (V043). Lets policy rules be authored
 * against readable canonical roles while the duty token carries the raw template id.
 *
 * <p>Read-mostly: the catalog is cached per {@code (tenant|roleTemplateId)} on first
 * lookup. Misses are cached as empty to avoid repeated queries on the hot decision path.
 * A cache miss simply means "no canonical mapping" — the raw template id is still folded
 * into the effective roles by the PolicyEngine, so this is purely additive.</p>
 */
@Service
public class RoleTemplateCatalog {

    private static final Logger log = LoggerFactory.getLogger(RoleTemplateCatalog.class);

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public RoleTemplateCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Canonical role(s) for a template id, or an empty list if none / on error. */
    public List<String> resolve(UUID tenantId, String roleTemplateId) {
        if (tenantId == null || roleTemplateId == null || roleTemplateId.isBlank()) {
            return List.of();
        }
        String key = tenantId + "|" + roleTemplateId;
        return cache.computeIfAbsent(key, k -> load(tenantId, roleTemplateId));
    }

    private List<String> load(UUID tenantId, String roleTemplateId) {
        try {
            List<String> roles = jdbcTemplate.queryForList(
                    "SELECT canonical_role FROM tshepo_authz.role_template_catalog "
                            + "WHERE tenant_id = ? AND role_template_id = ? AND active",
                    String.class, tenantId, roleTemplateId);
            return List.copyOf(roles);
        } catch (Exception e) {
            log.warn("Role-template catalog lookup failed for {} (treated as empty): {}",
                    roleTemplateId, e.getMessage());
            return List.of();
        }
    }
}
