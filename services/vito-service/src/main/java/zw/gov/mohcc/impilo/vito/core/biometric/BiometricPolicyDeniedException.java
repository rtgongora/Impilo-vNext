package zw.gov.mohcc.impilo.vito.core.biometric;

/**
 * Raised when Tshepo biometric policy forbids the requested operation for the workflow/context.
 */
public class BiometricPolicyDeniedException extends RuntimeException {

    public BiometricPolicyDeniedException(String message) {
        super(message);
    }
}
