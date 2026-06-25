package zw.gov.mohcc.impilo.oros.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.persistence.entity.AdminConfigEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.AdminConfigRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

/**
 * Manages tenant/facility-scoped diagnostics admin configuration (routing rules, critical-result
 * escalation rules). Config is stored as JSON and round-trips intact; facility-scoped config falls
 * back to the tenant-wide default on read.
 */
@Service
public class AdminConfigService {

    public static final String ROUTING_RULES = "ROUTING_RULES";
    public static final String CRITICAL_ESCALATION_RULES = "CRITICAL_ESCALATION_RULES";
    /** Service catalogue & fulfilment directory: imaging providers, labs, procedure units,
     *  specialist/allied teams, blood bank — with capabilities, tests/procedures, specimen types,
     *  modalities, hours, return method, onboarded + integration status, restrictions (spec §9). */
    public static final String SERVICE_CATALOGUE = "SERVICE_CATALOGUE";
    /** Test / procedure / service orderable catalogue (codes the order forms pick from). */
    public static final String ORDERABLE_CATALOGUE = "ORDERABLE_CATALOGUE";
    /** Specimen-type configuration (accepted specimen types, containers, handling). */
    public static final String SPECIMEN_CONFIG = "SPECIMEN_CONFIG";

    private static final Logger log = LoggerFactory.getLogger(AdminConfigService.class);

    private final AdminConfigRepository repository;

    public AdminConfigService(AdminConfigRepository repository) {
        this.repository = repository;
    }

    /** Read config JSON for a type; facility-scoped first, then tenant default; "{}" if none. */
    public String get(String configType, UUID facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        if (facilityId != null) {
            var scoped = repository.findByTenantIdAndFacilityIdAndConfigType(ctx.tenantId(), facilityId, configType);
            if (scoped.isPresent()) {
                return scoped.get().getConfigJson();
            }
        }
        return repository.findByTenantIdAndFacilityIdIsNullAndConfigType(ctx.tenantId(), configType)
                .map(AdminConfigEntity::getConfigJson)
                .orElse("{}");
    }

    /** Upsert config JSON for a type (facility-scoped or tenant-wide when facilityId is null). */
    @Transactional
    public String put(String configType, UUID facilityId, String configJson) {
        TrustContext ctx = TrustContextHolder.require();
        AdminConfigEntity entity = (facilityId != null
                ? repository.findByTenantIdAndFacilityIdAndConfigType(ctx.tenantId(), facilityId, configType)
                : repository.findByTenantIdAndFacilityIdIsNullAndConfigType(ctx.tenantId(), configType))
                .orElseGet(AdminConfigEntity::new);

        entity.setTenantId(ctx.tenantId());
        entity.setFacilityId(facilityId);
        entity.setConfigType(configType);
        entity.setConfigJson(configJson != null && !configJson.isBlank() ? configJson : "{}");
        entity.setUpdatedBy(ctx.actorId());
        entity = repository.save(entity);

        log.info("Admin config saved: type={}, facility={}, actor={}", configType, facilityId, ctx.actorId());
        return entity.getConfigJson();
    }
}
