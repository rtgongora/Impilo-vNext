package zw.gov.mohcc.impilo.mushex.service;

import zw.gov.mohcc.impilo.mushex.domain.entity.ReceiptEntity;

import java.util.List;

/**
 * Service for generating and retrieving payment receipts.
 */
public interface ReceiptService {

    /**
     * Get all receipts associated with a payment intent.
     */
    List<ReceiptEntity> getReceiptsByIntentId(String intentId);
}
