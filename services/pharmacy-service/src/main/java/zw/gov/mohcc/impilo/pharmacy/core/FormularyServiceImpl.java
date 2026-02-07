package zw.gov.mohcc.impilo.pharmacy.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.FormularyEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.FormularyRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages formulary checks and retrievals.
 *
 * <p>Determines whether a drug is allowed at a given facility by checking
 * two levels of formulary rules:</p>
 * <ol>
 *   <li><b>Facility-level</b>: specific rules for the given facility</li>
 *   <li><b>Tenant-level</b>: tenant-wide defaults (facility_id IS NULL)</li>
 * </ol>
 *
 * <p>Facility-level rules take precedence. If no facility-level rule
 * exists, the tenant-level rule is consulted. If neither exists, the
 * drug is considered not allowed.</p>
 */
@Service
public class FormularyServiceImpl implements FormularyService {

    private static final Logger log = LoggerFactory.getLogger(FormularyServiceImpl.class);

    private final FormularyRepository formularyRepository;

    public FormularyServiceImpl(FormularyRepository formularyRepository) {
        this.formularyRepository = formularyRepository;
    }

    @Override
    public boolean isAllowed(UUID tenantId, UUID facilityId, String drugCode) {
        // Check facility-level formulary first
        Optional<FormularyEntity> facilityEntry =
                formularyRepository.findByTenantIdAndFacilityIdAndDrugCode(tenantId, facilityId, drugCode);
        if (facilityEntry.isPresent()) {
            boolean allowed = facilityEntry.get().isAllowed();
            log.debug("Formulary check (facility): tenantId={}, facilityId={}, drugCode={}, allowed={}",
                    tenantId, facilityId, drugCode, allowed);
            return allowed;
        }

        // Fall back to tenant-level formulary
        Optional<FormularyEntity> tenantEntry =
                formularyRepository.findByTenantIdAndFacilityIdIsNullAndDrugCode(tenantId, drugCode);
        if (tenantEntry.isPresent()) {
            boolean allowed = tenantEntry.get().isAllowed();
            log.debug("Formulary check (tenant): tenantId={}, drugCode={}, allowed={}",
                    tenantId, drugCode, allowed);
            return allowed;
        }

        log.debug("Formulary check (not found): tenantId={}, facilityId={}, drugCode={}, default=false",
                tenantId, facilityId, drugCode);
        return false;
    }

    @Override
    public List<FormularyEntity> getFormulary(UUID tenantId) {
        return formularyRepository.findByTenantIdAndAllowedTrue(tenantId);
    }
}
