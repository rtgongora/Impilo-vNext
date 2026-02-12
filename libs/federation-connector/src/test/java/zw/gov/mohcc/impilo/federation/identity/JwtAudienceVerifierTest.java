package zw.gov.mohcc.impilo.federation.identity;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtAudienceVerifierTest {

    private final JwtAudienceVerifier verifier = new JwtAudienceVerifier();

    @Test
    void returnsTrueForFederationAudienceString() {
        String jwt = buildJwt("{\"aud\":\"federation\",\"sub\":\"pod-1\"}");
        assertTrue(verifier.hasFederationAudience(jwt));
    }

    @Test
    void returnsTrueForFederationAudienceArray() {
        String jwt = buildJwt("{\"aud\":[\"api\",\"federation\"],\"sub\":\"pod-1\"}");
        assertTrue(verifier.hasFederationAudience(jwt));
    }

    @Test
    void returnsFalseForMissingAudience() {
        String jwt = buildJwt("{\"sub\":\"pod-1\"}");
        assertFalse(verifier.hasFederationAudience(jwt));
    }

    @Test
    void returnsFalseForWrongAudience() {
        String jwt = buildJwt("{\"aud\":\"api\",\"sub\":\"pod-1\"}");
        assertFalse(verifier.hasFederationAudience(jwt));
    }

    @Test
    void returnsFalseForNullToken() {
        assertFalse(verifier.hasFederationAudience(null));
    }

    @Test
    void returnsFalseForBlankToken() {
        assertFalse(verifier.hasFederationAudience("  "));
    }

    @Test
    void returnsFalseForMalformedJwt() {
        assertFalse(verifier.hasFederationAudience("not.a.jwt"));
    }

    private String buildJwt(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes());
        return header + "." + payload + ".signature";
    }
}
