package zw.gov.mohcc.impilo.varapi.api.webhook;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundoSignatureUtilTest {

    @Test
    void hmac_is_stable_for_known_vector() {
        String secret = "test-secret";
        byte[] body = "{\"tenantId\":\"00000000-0000-0000-0000-000000000001\"}".getBytes(StandardCharsets.UTF_8);
        String hex = FundoSignatureUtil.hmacSha256Hex(secret, body);
        assertFalse(hex.isBlank());
        assertTrue(hex.matches("[0-9a-f]{64}"));
    }

    @Test
    void constantTimeEquals_detects_mismatch() {
        assertTrue(FundoSignatureUtil.constantTimeEquals("ab", "ab"));
        assertFalse(FundoSignatureUtil.constantTimeEquals("ab", "ac"));
        assertFalse(FundoSignatureUtil.constantTimeEquals("a", "ab"));
    }
}
