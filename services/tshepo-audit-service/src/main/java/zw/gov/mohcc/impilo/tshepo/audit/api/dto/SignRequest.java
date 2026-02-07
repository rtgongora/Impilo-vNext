package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

/**
 * Request payload sent to tshepo-keys-service POST /v1/sign.
 */
public record SignRequest(
        String payload,
        String algorithm
) {
    public static SignRequest ed25519(String payload) {
        return new SignRequest(payload, "Ed25519");
    }
}
