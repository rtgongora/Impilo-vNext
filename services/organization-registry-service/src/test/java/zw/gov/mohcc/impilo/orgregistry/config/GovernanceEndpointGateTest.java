package zw.gov.mohcc.impilo.orgregistry.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proof for the Wave 1B safe default.
 *
 * <p>The governance API takes its actor and scope from headers that only Envoy's ext_authz
 * makes trustworthy, and this estate runs with {@code extAuthz.enabled: false}. Until that
 * changes, the endpoints must be shut — and shut is the state you get by doing nothing,
 * because a control that has to be remembered is not a control.
 */
class GovernanceEndpointGateTest {

    @Test
    @DisplayName("the default is CLOSED — no configuration required to be safe")
    void closedByDefault() {
        GovernanceEndpointGate gate = new GovernanceEndpointGate(false, false);
        assertThat(gate.open()).isFalse();
        assertThat(gate.closedReason()).contains("not enabled on this deployment");
        assertThat(gate.requiredAction()).contains("ext_authz");
    }

    @Test
    @DisplayName("enabling the capability alone does NOT open it — the enforcement path must be asserted")
    void enabledAloneIsNotEnough() {
        GovernanceEndpointGate gate = new GovernanceEndpointGate(true, false);
        assertThat(gate.open())
                .as("a single enabled=true is exactly the flag someone flips to make a smoke test pass")
                .isFalse();
        assertThat(gate.closedReason())
                .contains("ext_authz")
                .contains("a merely authenticated caller could assert any actor");
    }

    @Test
    @DisplayName("asserting ext_authz alone does not open it either")
    void extAuthzAloneIsNotEnough() {
        GovernanceEndpointGate gate = new GovernanceEndpointGate(false, true);
        assertThat(gate.open()).isFalse();
        assertThat(gate.closedReason()).contains("not enabled on this deployment");
    }

    @Test
    @DisplayName("both conditions, deliberately set, open it")
    void bothConditionsOpenIt() {
        GovernanceEndpointGate gate = new GovernanceEndpointGate(true, true);
        assertThat(gate.open()).isTrue();
    }

    @Test
    @DisplayName("the closed reason names the real condition, never a generic unavailability")
    void closedReasonIsSpecific() {
        assertThat(new GovernanceEndpointGate(true, false).closedReason())
                .as("an operator must be able to act on this without reading the source")
                .contains("impilo.governance.trust-domain.ext-authz-verified");
        assertThat(new GovernanceEndpointGate(false, false).closedReason())
                .contains("impilo.governance.trust-domain.enabled");
    }
}
