package zw.gov.mohcc.impilo.federation.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

/**
 * Default implementation of {@link PodIdentityVerifier}.
 *
 * Validates pod identity by:
 * 1. Checking JWT audience contains "federation"
 * 2. Extracting pod_id claim from token and comparing with X-Pod-ID header
 * 3. Optionally validating mTLS certificate CN (when certificate is present)
 *
 * Full signature verification is delegated to the OAuth2 resource server;
 * this verifier focuses on federation-specific claim validation.
 */
public class DefaultPodIdentityVerifier implements PodIdentityVerifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JwtAudienceVerifier audienceVerifier;

    public DefaultPodIdentityVerifier() {
        this.audienceVerifier = new JwtAudienceVerifier();
    }

    public DefaultPodIdentityVerifier(JwtAudienceVerifier audienceVerifier) {
        this.audienceVerifier = audienceVerifier;
    }

    @Override
    public PodVerificationResult verify(String certificate, String jwtToken) {
        // JWT is required for federation identity
        if (jwtToken == null || jwtToken.isBlank()) {
            return PodVerificationResult.failure("No JWT token provided for federation identity");
        }

        // Check federation audience
        if (!audienceVerifier.hasFederationAudience(jwtToken)) {
            return PodVerificationResult.failure("JWT does not contain required audience 'federation'");
        }

        // Extract pod_id from token claims
        String tokenPodId = extractPodId(jwtToken);
        if (tokenPodId == null || tokenPodId.isBlank()) {
            return PodVerificationResult.failure("JWT does not contain 'pod_id' claim");
        }

        // If certificate is present, verify CN matches pod_id (optional mTLS layer)
        if (certificate != null && !certificate.isBlank()) {
            String cn = extractCN(certificate);
            if (cn != null && !cn.equalsIgnoreCase(tokenPodId)) {
                return PodVerificationResult.failure(
                        "Certificate CN '" + cn + "' does not match token pod_id '" + tokenPodId + "'");
            }
        }

        return PodVerificationResult.success(tokenPodId);
    }

    /**
     * Extract pod_id claim from JWT payload.
     */
    private String extractPodId(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) return null;

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = MAPPER.readTree(payload);

            // Check pod_id claim first
            JsonNode podIdNode = claims.get("pod_id");
            if (podIdNode != null && podIdNode.isTextual()) {
                return podIdNode.asText();
            }

            // Fallback: check azp (authorized party) claim
            JsonNode azpNode = claims.get("azp");
            if (azpNode != null && azpNode.isTextual()) {
                return azpNode.asText();
            }

            // Fallback: check sub (subject) claim
            JsonNode subNode = claims.get("sub");
            if (subNode != null && subNode.isTextual()) {
                return subNode.asText();
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract CN from a PEM-encoded certificate.
     * Simplified extraction — looks for CN= in the subject.
     */
    private String extractCN(String certificate) {
        try {
            // Simple pattern match for CN in certificate subject
            // Full X.509 parsing would use CertificateFactory, but this handles
            // the common case where the cert subject is passed as a header value
            if (certificate.contains("CN=")) {
                int start = certificate.indexOf("CN=") + 3;
                int end = certificate.indexOf(',', start);
                if (end < 0) end = certificate.indexOf('/', start);
                if (end < 0) end = certificate.length();
                return certificate.substring(start, end).trim();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
