package zw.gov.mohcc.impilo.mushex.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.mushex.api.dto.ReconImportLine;

import java.util.List;
import java.util.UUID;

/**
 * Service for importing bank/payment-provider statements and reconciling
 * them against internal payment intents.
 */
public interface ReconciliationService {

    /**
     * Import a batch of statement lines for reconciliation.
     */
    Object importStatement(UUID tenantId, List<ReconImportLine> lines);

    /**
     * Retrieve unmatched reconciliation entries for a tenant.
     */
    Page<Object> getUnmatched(UUID tenantId, Pageable pageable);

    /**
     * Manually match a reconciliation entry to a payment intent.
     */
    Object matchEntry(String reconId, String intentId);
}
