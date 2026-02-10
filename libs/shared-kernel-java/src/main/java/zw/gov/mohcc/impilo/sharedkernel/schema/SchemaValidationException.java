package zw.gov.mohcc.impilo.sharedkernel.schema;

/**
 * Thrown when an event or payload fails schema version validation.
 */
public class SchemaValidationException extends RuntimeException {

    public SchemaValidationException(String message) {
        super(message);
    }
}
