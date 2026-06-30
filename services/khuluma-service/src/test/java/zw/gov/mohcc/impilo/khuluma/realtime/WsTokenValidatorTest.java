package zw.gov.mohcc.impilo.khuluma.realtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WsTokenValidatorTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private JwtDecoder decoder;

    private WsTokenValidator validator() {
        return new WsTokenValidator(decoder);
    }

    private Jwt jwtWith(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(b::claim);
        return b.build();
    }

    @Test
    void valid_token_yields_identity_from_verified_claims() {
        when(decoder.decode("good")).thenReturn(jwtWith(Map.of(
                "tenant_id", TENANT.toString(), "actor_id", "prov-1", "actor_type", "PROVIDER")));

        WsTokenValidator.WsIdentity id = validator().validate("good");

        assertThat(id).isNotNull();
        assertThat(id.tenantId()).isEqualTo(TENANT);
        assertThat(id.actorId()).isEqualTo("prov-1");
        assertThat(id.actorType()).isEqualTo("PROVIDER");
    }

    @Test
    void falls_back_to_sub_for_actor_and_defaults_actor_type() {
        when(decoder.decode("good")).thenReturn(jwtWith(Map.of("tenant_id", TENANT.toString(), "sub", "user-9")));
        WsTokenValidator.WsIdentity id = validator().validate("good");
        assertThat(id.actorId()).isEqualTo("user-9");
        assertThat(id.actorType()).isEqualTo("PROVIDER");
    }

    @Test
    void invalid_or_expired_token_is_rejected() {
        when(decoder.decode("bad")).thenThrow(new JwtException("expired"));
        assertThat(validator().validate("bad")).isNull();
    }

    @Test
    void blank_token_is_rejected_without_decoding() {
        assertThat(validator().validate(null)).isNull();
        assertThat(validator().validate("  ")).isNull();
    }

    @Test
    void token_missing_tenant_or_actor_claim_is_rejected() {
        when(decoder.decode("noTenant")).thenReturn(jwtWith(Map.of("actor_id", "a")));
        assertThat(validator().validate("noTenant")).isNull();
    }
}
