package zw.gov.mohcc.impilo.khuluma.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HMAC invite tokens: mint/verify round-trip, tamper + forgery rejection, expiry, tenant
 * binding, and role normalization (invites can only grant MEMBER/COHOST/OBSERVER — never HOST).
 */
class MeetingInviteServiceTest {

    private final MeetingInviteService service =
            new MeetingInviteService(new ObjectMapper(), "test-secret-32-bytes-minimum-ok!!");

    private final UUID tenant = UUID.randomUUID();
    private final UUID conversation = UUID.randomUUID();

    @Test
    void mintAndVerify_roundTrips() {
        var invite = service.mint(tenant, conversation, "COHOST", 3600L, "provider-a");
        var claims = service.verify(tenant, invite.token());
        assertThat(claims.conversationId()).isEqualTo(conversation);
        assertThat(claims.role()).isEqualTo("COHOST");
        assertThat(claims.mintedBy()).isEqualTo("provider-a");
        assertThat(claims.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void tamperedToken_isRejected() {
        var invite = service.mint(tenant, conversation, null, 3600L, "provider-a");
        String[] parts = invite.token().split("\\.");
        // Flip the payload (claim a different conversation) but keep the old signature.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"v\":1,\"t\":\"" + tenant + "\",\"c\":\"" + UUID.randomUUID()
                        + "\",\"r\":\"COHOST\",\"x\":9999999999,\"m\":\"x\"}").getBytes());
        assertThatThrownBy(() -> service.verify(tenant, forgedPayload + "." + parts[1]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void expiredToken_isRejected() {
        MeetingInviteService shortLived = new MeetingInviteService(new ObjectMapper(), "test-secret");
        // ttl <= 0 falls back to the default, so mint with 1s and verify after expiry boundary
        var invite = shortLived.mint(tenant, conversation, null, 1L, "provider-a");
        // Rebuild a token that is already expired by minting with a negative offset via reflection-free
        // approach: wait out the 1s TTL.
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        assertThatThrownBy(() -> shortLived.verify(tenant, invite.token()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void crossTenantToken_isRejected() {
        var invite = service.mint(tenant, conversation, null, 3600L, "provider-a");
        assertThatThrownBy(() -> service.verify(UUID.randomUUID(), invite.token()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void differentSecret_doesNotVerify() {
        MeetingInviteService other = new MeetingInviteService(new ObjectMapper(), "another-secret");
        var invite = service.mint(tenant, conversation, null, 3600L, "provider-a");
        assertThatThrownBy(() -> other.verify(tenant, invite.token()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roleClaims_areNormalized_hostNeverGrantable() {
        assertThat(service.mint(tenant, conversation, "HOST", 60L, "a").role()).isEqualTo("MEMBER");
        assertThat(service.mint(tenant, conversation, "observer", 60L, "a").role()).isEqualTo("OBSERVER");
        assertThat(service.mint(tenant, conversation, null, 60L, "a").role()).isEqualTo("MEMBER");
    }

    @Test
    void malformedTokens_areRejected() {
        assertThatThrownBy(() -> service.verify(tenant, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verify(tenant, "no-dot")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verify(tenant, "a.b.c")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verify(tenant, "!!!.###")).isInstanceOf(IllegalArgumentException.class);
    }
}
