package zw.gov.mohcc.impilo.tshepo.consent.exception;

/**
 * Thrown when the provided FHIR R4 Consent JSON is invalid or cannot be parsed.
 */
public class InvalidFhirConsentException extends RuntimeException {

    public InvalidFhirConsentException(String message) {
        super(message);
    }

    public InvalidFhirConsentException(String message, Throwable cause) {
        super(message, cause);
    }
}
