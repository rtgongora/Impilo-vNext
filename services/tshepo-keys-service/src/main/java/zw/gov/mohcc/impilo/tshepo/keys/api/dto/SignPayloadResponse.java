package zw.gov.mohcc.impilo.tshepo.keys.api.dto;

/**
 * Response containing the signature or JWS compact serialization.
 */
public record SignPayloadResponse(
        String keyId,
        String algorithm,
        /** Base64url-encoded raw signature, or full JWS compact serialization if requested. */
        String signature
) {}
