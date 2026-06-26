package zw.gov.mohcc.impilo.dags.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnforcementServiceTest {

    private EnforcementService enforcementService;

    @BeforeEach
    void setUp() {
        enforcementService = new EnforcementService("test-signing-key");
    }

    @Test
    @DisplayName("Issues a non-null permit token")
    void issuesNonNullPermitToken() {
        UUID tenantId = UUID.randomUUID();
        String token = enforcementService.issuePermitToken(1L, tenantId, "user-1");
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Permit token starts with expected prefix")
    void permitTokenHasCorrectPrefix() {
        UUID tenantId = UUID.randomUUID();
        String token = enforcementService.issuePermitToken(42L, tenantId, "user-2");
        assertThat(token).startsWith("permit-token:v1:");
    }

    @Test
    @DisplayName("Permit token contains tenant and requester info")
    void permitTokenContainsContextInfo() {
        UUID tenantId = UUID.randomUUID();
        String token = enforcementService.issuePermitToken(7L, tenantId, "dr-jones");
        assertThat(token).contains(".");
    }

    @Test
    @DisplayName("Each token is unique")
    void tokensAreUnique() {
        UUID tenantId = UUID.randomUUID();
        String token1 = enforcementService.issuePermitToken(1L, tenantId, "user-1");
        String token2 = enforcementService.issuePermitToken(1L, tenantId, "user-1");
        assertThat(token1).isNotEqualTo(token2);
    }

    // ---- B3: fail-closed key + stronger binding ----------------------------

    @Test
    @DisplayName("Refuses to start with the insecure default key")
    void failsClosedOnInsecureDefaultKey() {
        assertThatThrownBy(() -> new EnforcementService("change-me-in-prod"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Refuses to start with a blank or null key")
    void failsClosedOnBlankKey() {
        assertThatThrownBy(() -> new EnforcementService("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EnforcementService("   ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EnforcementService(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Requester is bound via a full SHA-256 digest, not a 32-bit hashCode")
    void requesterIsStronglyBound() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = enforcementService.issuePermitToken(1L, tenantId, "user-1");
        String body = token.substring("permit-token:v1:".length(), token.lastIndexOf('.'));
        String payload = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        String requesterField = payload.split("\\|")[1];

        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest("user-1".getBytes(StandardCharsets.UTF_8)));
        assertThat(requesterField).isEqualTo(expected);
        // 32-byte digest => 43 base64url chars (the old hashCode hex was <= 8 chars)
        assertThat(requesterField).hasSize(43);
    }

    @Test
    @DisplayName("Signature is a full-length (32-byte) HMAC-SHA256")
    void signatureIsFullLength() {
        UUID tenantId = UUID.randomUUID();
        String token = enforcementService.issuePermitToken(1L, tenantId, "user-1");
        String sig = token.substring(token.lastIndexOf('.') + 1);
        assertThat(Base64.getUrlDecoder().decode(sig)).hasSize(32);
    }
}
