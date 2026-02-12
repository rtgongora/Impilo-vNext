package zw.gov.mohcc.impilo.federation.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

/**
 * Simple JWT audience verifier for federation tokens.
 *
 * Checks that the token's "aud" claim includes "federation".
 * This is a lightweight check — full signature validation is handled
 * by the OAuth2 resource server or Keycloak adapter.
 */
public class JwtAudienceVerifier {

    private static final String REQUIRED_AUDIENCE = "federation";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Verify that the JWT token contains the federation audience.
     *
     * @param jwtToken the raw JWT (without "Bearer " prefix)
     * @return true if aud contains "federation"
     */
    public boolean hasFederationAudience(String jwtToken) {
        if (jwtToken == null || jwtToken.isBlank()) {
            return false;
        }

        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length < 2) return false;

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = MAPPER.readTree(payload);
            JsonNode aud = claims.get("aud");

            if (aud == null) return false;

            if (aud.isTextual()) {
                return REQUIRED_AUDIENCE.equals(aud.asText());
            }

            if (aud.isArray()) {
                for (JsonNode a : aud) {
                    if (REQUIRED_AUDIENCE.equals(a.asText())) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
