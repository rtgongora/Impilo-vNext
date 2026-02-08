package zw.gov.mohcc.impilo.mushex.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.mushex.domain.entity.LedgerEntryEntity;

import java.util.UUID;

/**
 * Service for the double-entry ledger that records all payment movements.
 */
public interface LedgerService {

    /**
     * Retrieve ledger entries for a tenant, paginated.
     */
    Page<LedgerEntryEntity> getEntries(UUID tenantId, Pageable pageable);
}
