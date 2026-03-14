package zw.gov.mohcc.impilo.security.input;

/**
 * Thrown when input validation fails. Carries a machine-readable error code
 * and the field name that failed validation.
 * <p>
 * Messages intentionally omit the rejected value to prevent PII/payload leakage.
 */
public class InputValidationException extends RuntimeException {

    private final String errorCode;
    private final String fieldName;

    public InputValidationException(String errorCode, String fieldName) {
        super("Input validation failed: " + errorCode + " on field '" + fieldName + "'");
        this.errorCode = errorCode;
        this.fieldName = fieldName;
    }

    public String getErrorCode() { return errorCode; }
    public String getFieldName() { return fieldName; }
}
