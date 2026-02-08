package zw.gov.mohcc.impilo.mushex.service;

import zw.gov.mohcc.impilo.mushex.domain.entity.RefundEntity;

import java.math.BigDecimal;

/**
 * Service for creating and processing refunds against payment intents.
 */
public interface RefundService {

    /**
     * Create a refund for the given payment intent.
     */
    RefundEntity createRefund(String intentId, BigDecimal amount, String reason);
}
