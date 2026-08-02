package zw.gov.mohcc.impilo.shared.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The estate mints no {@code aud} claim today, so this validator and the Keycloak audience mapper
 * are one control in two halves. These tests pin both the enforcement and the sequencing that
 * stops the first half from taking the estate down on its own.
 */
class WorkloadAudienceValidatorTest {

    private static final String AUD = "urn:impilo:tshepo:workforce-governance-service";

    private static Jwt jwt(List<String> audiences) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("sub", "service-account-vashandi-workforce-service");
        if (audiences != null) {
            b.audience(audiences);
        }
        return b.build();
    }

    @Test
    @DisplayName("a token carrying the required audience is accepted")
    void acceptsMatchingAudience() {
        var r = new WorkloadAudienceValidator(AUD).validate(jwt(List.of(AUD)));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("a token minted for another service is rejected — this is the replay control")
    void rejectsForeignAudience() {
        var r = new WorkloadAudienceValidator(AUD)
                .validate(jwt(List.of("urn:impilo:tshepo:some-other-service")));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors().iterator().next().getDescription()).contains(AUD);
    }

    @Test
    @DisplayName("a token with NO audience is rejected once enforcing — today every token looks like this")
    void rejectsMissingAudience() {
        // The realm has no oidc-audience-mapper, so every token currently minted has no `aud`.
        // This is exactly why the validator must not be switched on before the mappers exist.
        assertThat(new WorkloadAudienceValidator(AUD).validate(jwt(null)).hasErrors()).isTrue();
        assertThat(new WorkloadAudienceValidator(AUD).validate(jwt(List.of())).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("no configured audience means not-yet-opted-in, and passes rather than self-inflicting an outage")
    void unconfiguredIsInert() {
        for (String unset : new String[]{null, "", "   "}) {
            var v = new WorkloadAudienceValidator(unset);
            assertThat(v.isEnforcing()).isFalse();
            assertThat(v.validate(jwt(null)).hasErrors()).isFalse();
            assertThat(v.validate(jwt(List.of("anything"))).hasErrors()).isFalse();
        }
    }

    @Test
    @DisplayName("a configured audience IS enforcing — the inert path must not swallow a real config")
    void configuredIsEnforcing() {
        assertThat(new WorkloadAudienceValidator(AUD).isEnforcing()).isTrue();
        // Whitespace is trimmed rather than treated as a distinct audience.
        assertThat(new WorkloadAudienceValidator("  " + AUD + "  ").validate(jwt(List.of(AUD))).hasErrors())
                .isFalse();
    }

    @Test
    @DisplayName("the failure message does not disclose which audiences the token DID carry")
    void failureDoesNotLeakPresentedAudiences() {
        // Echoing them back would tell an attacker which services a stolen token is accepted by.
        var r = new WorkloadAudienceValidator(AUD).validate(jwt(List.of("urn:impilo:tshepo:vito-service")));
        String desc = r.getErrors().iterator().next().getDescription();
        assertThat(desc).doesNotContain("vito-service");
    }

    @Test
    @DisplayName("one matching audience among several is enough")
    void acceptsWhenOneOfManyMatches() {
        var r = new WorkloadAudienceValidator(AUD)
                .validate(jwt(List.of("account", AUD, "urn:impilo:tshepo:other")));
        assertThat(r.hasErrors()).isFalse();
    }
}
