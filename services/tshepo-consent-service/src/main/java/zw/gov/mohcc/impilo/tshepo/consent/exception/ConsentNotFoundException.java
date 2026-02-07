package zw.gov.mohcc.impilo.tshepo.consent.exception;

import java.util.UUID;

/**
 * Thrown when a consent directive cannot be found by ID.
 */
public class ConsentNotFoundException extends RuntimeException {

    private final UUID consentId;

    public ConsentNotFoundException(UUID consentId) {
        super("Consent directive not found: " + consentId);
        this.consentId = consentId;
    }

    public UUID getConsentId() {
        return consentId;
    }
}
