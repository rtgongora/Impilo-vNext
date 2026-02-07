package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityConfigVersionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.TenantConfigDefaultEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceConfigOverrideEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityConfigVersionRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.TenantConfigDefaultRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceConfigOverrideRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the effective configuration by merging three layers:
 * 1. Tenant defaults (base)
 * 2. Facility config version (overrides tenant defaults)
 * 3. Workspace config override (overrides facility config)
 *
 * <p>Deep merge: nested maps are merged at each key level, with later layers
 * overriding earlier layers at the same path. Non-map values are replaced entirely.</p>
 */
@Service
public class ConfigResolutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ConfigResolutionEngine.class);

    private final TenantConfigDefaultRepository tenantConfigRepository;
    private final FacilityConfigVersionRepository facilityConfigRepository;
    private final WorkspaceConfigOverrideRepository workspaceConfigRepository;

    public ConfigResolutionEngine(TenantConfigDefaultRepository tenantConfigRepository,
                                   FacilityConfigVersionRepository facilityConfigRepository,
                                   WorkspaceConfigOverrideRepository workspaceConfigRepository) {
        this.tenantConfigRepository = tenantConfigRepository;
        this.facilityConfigRepository = facilityConfigRepository;
        this.workspaceConfigRepository = workspaceConfigRepository;
    }

    /**
     * Resolve effective config: tenant defaults → facility config → workspace overrides.
     *
     * @param tenantId    the tenant UUID
     * @param facilityId  the facility ID
     * @param workspaceId optional workspace UUID (null to skip workspace layer)
     * @return the merged effective configuration
     */
    @Transactional(readOnly = true)
    public Map<String, Object> resolve(UUID tenantId, Long facilityId, UUID workspaceId) {
        log.debug("Resolving config for tenant={}, facility={}, workspace={}", tenantId, facilityId, workspaceId);

        // Layer 1: Tenant defaults
        Map<String, Object> result = new HashMap<>();
        tenantConfigRepository.findByTenantId(tenantId).ifPresent(tenantConfig -> {
            if (tenantConfig.getConfigData() != null) {
                result.putAll(tenantConfig.getConfigData());
            }
        });

        // Layer 2: Facility config (active version)
        facilityConfigRepository.findLatestActive(facilityId).ifPresent(facilityConfig -> {
            if (facilityConfig.getConfigData() != null) {
                deepMerge(result, facilityConfig.getConfigData());
            }
        });

        // Layer 3: Workspace override (only if workspaceId provided)
        if (workspaceId != null) {
            workspaceConfigRepository.findByWorkspaceId(workspaceId).ifPresent(workspaceConfig -> {
                if (workspaceConfig.getConfigData() != null) {
                    deepMerge(result, workspaceConfig.getConfigData());
                }
            });
        }

        log.debug("Resolved config with {} keys for facility {} workspace {}",
                result.size(), facilityId, workspaceId);
        return Collections.unmodifiableMap(result);
    }

    /**
     * Deep merge: for each key in the override map, if both base and override have a Map value,
     * recursively merge. Otherwise, the override value replaces the base value.
     */
    @SuppressWarnings("unchecked")
    static void deepMerge(Map<String, Object> base, Map<String, Object> overrides) {
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String key = entry.getKey();
            Object overrideValue = entry.getValue();
            Object baseValue = base.get(key);

            if (baseValue instanceof Map && overrideValue instanceof Map) {
                Map<String, Object> baseCopy = new HashMap<>((Map<String, Object>) baseValue);
                deepMerge(baseCopy, (Map<String, Object>) overrideValue);
                base.put(key, baseCopy);
            } else {
                base.put(key, overrideValue);
            }
        }
    }
}
