package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

/**
 * Response payload from tshepo-keys-service POST /v1/sign.
 */
public record SignResponse(
        String signature,
        String keyId,
        String algorithm
) {
}
