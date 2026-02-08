package zw.gov.mohcc.impilo.mushex.service;

import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceTokenEntity;

/**
 * Service for issuing and claiming remittance slips (token + OTP)
 * that allow third-party collection of payments.
 */
public interface RemittanceService {

    /**
     * Issue a new remittance slip for a given payment intent.
     * Generates a token and OTP, stores their hashes, and returns the entity.
     */
    RemittanceTokenEntity issueRemittanceSlip(String intentId);

    /**
     * Claim a remittance using the raw token and OTP.
     * Verifies the hashes, marks the remittance as claimed, and records payment on the intent.
     */
    RemittanceTokenEntity claimRemittance(String token, String otp);
}
