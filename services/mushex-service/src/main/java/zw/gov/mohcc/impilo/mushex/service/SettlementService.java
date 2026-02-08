package zw.gov.mohcc.impilo.mushex.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for running settlement computations and releasing payouts
 * for a given period.
 */
public interface SettlementService {

    /**
     * Initiate a new settlement run for the given tenant and date range.
     */
    Object runSettlement(UUID tenantId, LocalDate periodStart, LocalDate periodEnd);

    /**
     * Retrieve a settlement by its ID, including computed payouts.
     */
    Object getSettlement(String settlementId);

    /**
     * Release all computed payouts for a settlement, initiating actual transfers.
     */
    Object releasePayouts(String settlementId);
}
