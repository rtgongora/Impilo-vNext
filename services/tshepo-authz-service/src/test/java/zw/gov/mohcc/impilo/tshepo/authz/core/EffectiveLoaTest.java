package zw.gov.mohcc.impilo.tshepo.authz.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthzInternalRequest;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The effective Level of Assurance is the stronger of two signals, and until now it was one.
 *
 * <p>{@code identityLoa} read only the propagated {@code X-Assurance-Level} header, while the
 * comment above the {@code min_loa} check claimed it already combined that with the session's
 * ACR-derived login level. The header is resolved server-side for CITIZEN actors only —
 * {@code AssuranceLevelResolutionInterceptor} skips providers, and the ACR fallback its javadoc
 * assumes was never wired — so for a provider it was absent and every {@code min_loa} rule failed
 * closed however strongly they had authenticated. Seven active ALLOW rules carry
 * {@code min_loa: 2}; not one of them could fire.
 *
 * <p>Direction matters: taking the stronger of the two can only raise the effective LoA. It must
 * never lower one, and it must not invent assurance where neither signal exists.
 */
class EffectiveLoaTest {

    private static AuthzInternalRequest request(String propagatedLevel, Integer aal) {
        AuthzInternalRequest base = new AuthzInternalRequest(
                null, "actor-1", "PROVIDER", List.of(), "TREATMENT", null, null, null, null, null,
                "GET", "/internal/v1/anything", "GET:/internal/v1/anything", "anything", null,
                0, "sess-1", null, null, null, null, null, null, propagatedLevel, null, null, null);
        return aal == null ? base
                : base.withAuthenticationAssurance(new AuthenticationAssurance(
                        aal, List.of("pwd"), Instant.now(), null, false, "sess-1", "flow-1"));
    }

    @Test
    @DisplayName("a strongly-authenticated provider with no propagated level now clears min_loa 2")
    void loginLevelCountsWhenTheHeaderIsAbsent() {
        // Exactly the case that left seven rules dead: provider, no X-Assurance-Level, AAL2 login.
        assertThat(PolicyEngine.identityLoa(request(null, 2)))
                .as("previously 0 — the header was absent and the login level was ignored")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("the propagated level still wins when it is the stronger of the two")
    void propagatedLevelWinsWhenStronger() {
        assertThat(PolicyEngine.identityLoa(request("LOA3", 1))).isEqualTo(3);
    }

    @Test
    @DisplayName("a weak login never demotes a verified identity")
    void loginLevelNeverLowers() {
        assertThat(PolicyEngine.identityLoa(request("LOA3", 0)))
                .as("max(), never overwrite")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("neither signal present is still zero — this widens, it does not invent assurance")
    void nothingKnownIsStillZero() {
        assertThat(PolicyEngine.identityLoa(request(null, null))).isZero();
    }
}
