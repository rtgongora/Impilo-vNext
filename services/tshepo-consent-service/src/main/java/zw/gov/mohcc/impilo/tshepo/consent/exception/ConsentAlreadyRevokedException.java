package zw.gov.mohcc.impilo.tshepo.consent.exception;

import java.util.UUID;

/**
 * Thrown when attempting to revoke or modify a consent directive that has already been revoked.
 */
public class ConsentAlreadyRevokedException extends RuntimeException {

    private final UUID consentId;

    public ConsentAlreadyRevokedException(UUID consentId) {
        super("Consent directive already revoked: " + consentId);
        this.consentId = consentId;
    }

    public UUID getConsentId() {
        return consentId;
    }
}
