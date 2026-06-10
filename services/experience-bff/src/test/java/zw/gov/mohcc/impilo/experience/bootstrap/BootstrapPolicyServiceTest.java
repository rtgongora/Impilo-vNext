package zw.gov.mohcc.impilo.experience.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapPolicyServiceTest {

    private final BootstrapPolicyService policyService = new BootstrapPolicyService();

    @Test
    void bootstrapRequiredWhenNoActiveNationalAdmin() {
        BootstrapProperties props = enabledProps();
        BootstrapDtos.BootstrapStateRecord state = openState(false);

        var decision = policyService.evaluateStatus(state, false, props);
        assertTrue(decision.allowed());
    }

    @Test
    void bootstrapBlockedWhenNationalAdminAlreadyExists() {
        BootstrapProperties props = enabledProps();
        BootstrapDtos.BootstrapStateRecord state = openState(false);

        var decision = policyService.evaluateStatus(state, true, props);
        assertFalse(decision.allowed());
        assertTrue(decision.blockedReasons().stream().anyMatch(r -> r.contains("already exists")));
    }

    @Test
    void bootstrapBlockedWhenClosedAndNotRecovery() {
        BootstrapProperties props = enabledProps();
        BootstrapDtos.BootstrapStateRecord state = closedState(false);

        var decision = policyService.evaluateStatus(state, false, props);
        assertFalse(decision.allowed());
        assertTrue(decision.blockedReasons().stream().anyMatch(r -> r.contains("closed")));
    }

    @Test
    void recoveryModeAllowsBootstrapWhenClosed() {
        BootstrapProperties props = enabledProps();
        BootstrapDtos.BootstrapStateRecord state = closedState(true);

        var decision = policyService.evaluateStatus(state, false, props);
        assertTrue(decision.allowed());
    }

    @Test
    void firstAdminRequiresNominatedEmailAndOrganisation() {
        BootstrapProperties props = enabledProps();
        BootstrapDtos.BootstrapStateRecord state = openState(false);
        var request = new BootstrapDtos.BootstrapFirstAdminRequest(
                "one_time_token",
                "token",
                null,
                Map.of(),
                Map.of("fullName", "Test Admin"),
                List.of("national_bootstrap_administrator"),
                "REF-1",
                true,
                "development"
        );

        var decision = policyService.evaluateFirstAdmin(state, false, props, request);
        assertFalse(decision.allowed());
    }

    @Test
    void recoveryOpenRequiresCeremonyReference() {
        BootstrapDtos.BootstrapStateRecord state = closedState(false);
        var decision = policyService.evaluateRecoveryOpen(state, "");
        assertFalse(decision.allowed());
    }

    private static BootstrapProperties enabledProps() {
        BootstrapProperties props = new BootstrapProperties();
        props.setEnabled(true);
        props.setEnvironment("development");
        props.setAllowedMethods("one_time_token,signed_authorisation");
        props.setRequireMfa(true);
        return props;
    }

    private static BootstrapDtos.BootstrapStateRecord openState(boolean recoveryMode) {
        return new BootstrapDtos.BootstrapStateRecord(
                "state-1",
                "tenant-1",
                true,
                false,
                null,
                recoveryMode,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of()
        );
    }

    private static BootstrapDtos.BootstrapStateRecord closedState(boolean recoveryMode) {
        return new BootstrapDtos.BootstrapStateRecord(
                "state-1",
                "tenant-1",
                false,
                true,
                "2026-06-10T00:00:00Z",
                recoveryMode,
                null,
                null,
                "admin-user-1",
                null,
                null,
                null,
                null,
                null,
                Map.of()
        );
    }
}
