package zw.gov.mohcc.impilo.costa.engine;

import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Strategy interface for cost computation engines.
 * Each engine computes unit cost for a given item using a specific methodology.
 */
public interface CostEngine {

    CostMethodType getMethodType();

    /**
     * Compute the cost for a given item.
     *
     * @param tenantId    the tenant context
     * @param facilityId  the facility context
     * @param msikaCode   the chargeable item code
     * @param qty         the quantity
     * @param context     additional context (facility_category, patient_category, etc.)
     * @return the cost result with full trace
     */
    CostResult compute(UUID tenantId, UUID facilityId, String msikaCode,
                       BigDecimal qty, Map<String, Object> context);
}
