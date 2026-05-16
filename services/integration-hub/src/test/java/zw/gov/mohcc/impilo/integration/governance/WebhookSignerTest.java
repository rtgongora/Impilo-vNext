package zw.gov.mohcc.impilo.integration.governance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.integration.governance.service.WebhookSigner;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the outbound webhook signer + verifier. No Spring context
 * required; the signer is a stateless component.
 */
class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    @DisplayName("sign() produces a deterministic header for a fixed timestamp + body + secret")
    void signIsDeterministic() {
        String secret = "shhh-test-secret";
        String body = "{\"hello\":\"impilo\"}";
        long ts = 1_700_000_000L;
        String a = signer.sign(secret, ts, body);
        String b = signer.sign(secret, ts, body);
        assertThat(a).isEqualTo(b);
        assertThat(a).startsWith("t=" + ts + ",v1=");
        assertThat(a).hasSizeGreaterThan(("t=" + ts + ",v1=").length() + 32);
    }

    @Test
    @DisplayName("sign() changes when the body changes")
    void differentBodyDifferentSignature() {
        long ts = 1_700_000_000L;
        String s1 = signer.sign("secret", ts, "{\"a\":1}");
        String s2 = signer.sign("secret", ts, "{\"a\":2}");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    @DisplayName("verify() accepts a freshly signed envelope")
    void verifyAcceptsFreshSignature() {
        String body = "{\"event\":\"lab.result.received\",\"id\":\"abc\"}";
        String header = signer.signNow("secret-1", body);
        assertThat(signer.verify("secret-1", body, header, 300)).isTrue();
    }

    @Test
    @DisplayName("verify() rejects when the body has been tampered")
    void verifyRejectsTamperedBody() {
        String body = "{\"event\":\"lab.result.received\",\"id\":\"abc\"}";
        String header = signer.signNow("secret-1", body);
        String tampered = body.replace("abc", "xyz");
        assertThat(signer.verify("secret-1", tampered, header, 300)).isFalse();
    }

    @Test
    @DisplayName("verify() rejects when the wrong secret is provided")
    void verifyRejectsWrongSecret() {
        String body = "{\"event\":\"x\"}";
        String header = signer.signNow("right-secret", body);
        assertThat(signer.verify("wrong-secret", body, header, 300)).isFalse();
    }

    @Test
    @DisplayName("verify() rejects when the timestamp is outside the allowed skew window")
    void verifyRejectsOldSignature() {
        String body = "{\"x\":1}";
        long old = Instant.now().getEpochSecond() - 3600;
        String header = signer.sign("s", old, body);
        assertThat(signer.verify("s", body, header, 60)).isFalse();
        assertThat(signer.verify("s", body, header, 7200)).isTrue();
    }

    @Test
    @DisplayName("verify() rejects malformed headers without throwing")
    void verifyRejectsMalformed() {
        assertThat(signer.verify("s", "{}", null, 300)).isFalse();
        assertThat(signer.verify("s", "{}", "", 300)).isFalse();
        assertThat(signer.verify("s", "{}", "t=NaN,v1=abc", 300)).isFalse();
        assertThat(signer.verify("s", "{}", "noequalshere", 300)).isFalse();
    }
}
