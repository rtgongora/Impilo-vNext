package zw.gov.mohcc.impilo.pharmacy.core;

import zw.gov.mohcc.impilo.pharmacy.persistence.entity.FormularyEntity;

import java.util.List;
import java.util.UUID;

/**
 * Service managing formulary checks and retrievals.
 *
 * <p>Determines whether a drug is allowed at a given facility based
 * on tenant-wide and facility-specific formulary rules.</p>
 */
public interface FormularyService {

    /**
     * Check if a drug is allowed on the formulary at the given facility.
     */
    boolean isAllowed(UUID tenantId, UUID facilityId, String drugCode);

    /**
     * Get all allowed formulary entries for a tenant.
     */
    List<FormularyEntity> getFormulary(UUID tenantId);
}
