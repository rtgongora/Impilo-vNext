package zw.gov.mohcc.impilo.rules.engine;

/**
 * Thrown when a rule expression cannot be parsed or is syntactically invalid.
 */
public class RuleExpressionInvalidException extends RuntimeException {

    public RuleExpressionInvalidException(String message) {
        super(message);
    }

    public RuleExpressionInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
