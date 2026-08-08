package zw.gov.mohcc.impilo.nhume.integration.writeback;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.nhume.integration.writeback.NhumeWriteBackGateway.WriteBackContext;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The trust posture of the Msika Flow write-backs.
 *
 * <p>Two things are asserted that were previously untrue: the call carries a credential of
 * its own rather than only whatever bearer happened to be on the inbound request, and it
 * declares a purpose narrow enough to be honest about what it does.
 */
class HttpNhumeWriteBackGatewayAuthTest {

    private static final String MSIKA = "http://msika-flow.test";

    private record Fixture(HttpNhumeWriteBackGateway gateway, MockRestServiceServer server) {}

    private Fixture fixture(WorkloadTokenProvider provider) {
        RestClient.Builder builder = HttpNhumeWriteBackGateway.defaultBuilder(5000, provider);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(
                new HttpNhumeWriteBackGateway("http://oros.test", "http://madi.test",
                        "http://pct.test", MSIKA, builder),
                server);
    }

    private static WorkloadTokenProvider providerReturning(String token) {
        WorkloadTokenProvider provider = mock(WorkloadTokenProvider.class);
        when(provider.getAccessToken()).thenReturn(Optional.ofNullable(token));
        return provider;
    }

    private static WriteBackContext ctx(String bearerToken) {
        return new WriteBackContext("11111111-1111-1111-1111-111111111111", "national-spine",
                "22222222-2222-2222-2222-222222222222", "actor-1", "PROVIDER",
                bearerToken, "delivery-1");
    }

    @Test
    void dispatchStatus_declaresOperations_notTheAllBypassingSystemPurpose() {
        Fixture f = fixture(providerReturning("workload-token"));
        f.server().expect(requestTo(MSIKA + "/internal/v1/msika-flow/orders/ORD-1/dispatch-status"))
                .andExpect(header("X-Purpose-Of-Use", "OPERATIONS"))
                .andRespond(withSuccess());

        WriteBackOutcome outcome = f.gateway()
                .msikaFlowDispatchStatus("ORD-1", "DELIVERED", "delivery-1", "pod-1", ctx(null));

        f.server().verify();
        assertThat(outcome.status()).isEqualTo(WriteBackOutcome.Status.OK);
    }

    @Test
    void selectionDeliveryStatus_declaresOperations_notTheAllBypassingSystemPurpose() {
        Fixture f = fixture(providerReturning("workload-token"));
        f.server().expect(requestTo(
                        MSIKA + "/internal/v1/msika-flow/selections/SEL-1/delivery-status"))
                .andExpect(header("X-Purpose-Of-Use", "OPERATIONS"))
                .andRespond(withSuccess());

        f.gateway().msikaFlowSelectionDeliveryStatus("SEL-1", "DELIVERED", "delivery-1",
                "pod-1", "A", ctx(null));

        f.server().verify();
    }

    @Test
    void withNoDelegatedBearer_theWorkloadsOwnTokenIsAttached() {
        Fixture f = fixture(providerReturning("workload-token"));
        f.server().expect(requestTo(MSIKA + "/internal/v1/msika-flow/orders/ORD-1/dispatch-status"))
                .andExpect(header("Authorization", "Bearer workload-token"))
                .andRespond(withSuccess());

        // A scheduled retry runs outside any request context, so ctx carries no bearer at all.
        f.gateway().msikaFlowDispatchStatus("ORD-1", "DELIVERED", "delivery-1", null, ctx(null));

        f.server().verify();
    }

    @Test
    void aDelegatedHumanTokenIsNeverOverwrittenByTheStrongerServiceCredential() {
        Fixture f = fixture(providerReturning("workload-token"));
        f.server().expect(requestTo(MSIKA + "/internal/v1/msika-flow/orders/ORD-1/dispatch-status"))
                .andExpect(header("Authorization", "Bearer human-token"))
                .andRespond(withSuccess());

        f.gateway().msikaFlowDispatchStatus("ORD-1", "DELIVERED", "delivery-1", null,
                ctx("Bearer human-token"));

        f.server().verify();
    }

    @Test
    void whenNoTokenCanBeMinted_theCallGoesOutBareAndTheCalleeRefusesIt() {
        // Failing closed at the callee is the point: the gateway must not invent a fallback.
        Fixture f = fixture(providerReturning(null));
        f.server().expect(requestTo(MSIKA + "/internal/v1/msika-flow/orders/ORD-1/dispatch-status"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess());

        f.gateway().msikaFlowDispatchStatus("ORD-1", "DELIVERED", "delivery-1", null, ctx(null));

        f.server().verify();
    }

    @Test
    void theClinicalWriteBacksKeepCareCoordination_andAlsoGainTheWorkloadCredential() {
        // The sibling header builder serves OROS / MADI / PCT — clinical cargo, HL7 COC.
        // This fix must not disturb its purpose, only give it a credential.
        Fixture f = fixture(providerReturning("workload-token"));
        f.server().expect(requestTo("http://pct.test/v1/referrals/REF-1/accept"))
                .andExpect(header("X-Purpose-Of-Use", "CARE_COORDINATION"))
                .andExpect(header("Authorization", "Bearer workload-token"))
                .andRespond(withSuccess());

        f.gateway().pctAcceptReferral("REF-1", ctx(null));

        f.server().verify();
    }
}
