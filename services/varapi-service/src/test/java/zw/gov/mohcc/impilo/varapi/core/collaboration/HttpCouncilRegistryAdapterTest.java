package zw.gov.mohcc.impilo.varapi.core.collaboration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilAdapterRegistrationEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilAdapterRegistrationRepository;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link HttpCouncilRegistryAdapter} exercised against a MOCK authority (MockRestServiceServer)
 * — no real endpoint is ever contacted. Proves the honest mapping of authority responses and,
 * critically, that transport failure yields UNREACHABLE (never a fabricated PASS).
 */
class HttpCouncilRegistryAdapterTest {

    private static final String ENDPOINT = "https://authority.example/verify";
    private final UUID tenantId = UUID.randomUUID();

    private CouncilAdapterRegistrationEntity enabledRegistration() {
        CouncilAdapterRegistrationEntity reg =
                new CouncilAdapterRegistrationEntity(tenantId, "MDPCZ", ENDPOINT);
        reg.setEnabled(true);
        return reg;
    }

    /** Builds an adapter whose per-call RestTemplate is bound to a mock server. */
    private record Harness(HttpCouncilRegistryAdapter adapter, MockRestServiceServer server) {}

    private Harness harness(CouncilAdapterRegistrationRepository repo) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        HttpCouncilRegistryAdapter adapter = new HttpCouncilRegistryAdapter(repo) {
            @Override
            protected RestTemplate restTemplateFor(CouncilAdapterRegistrationEntity registration) {
                return restTemplate;
            }
        };
        return new Harness(adapter, server);
    }

    private CouncilAdapterRegistrationRepository repoReturningRegistration() {
        CouncilAdapterRegistrationRepository repo = mock(CouncilAdapterRegistrationRepository.class);
        when(repo.findFirstByTenantIdAndCouncilCodeIgnoreCaseAndEnabledTrue(eq(tenantId), any()))
                .thenReturn(Optional.of(enabledRegistration()));
        return repo;
    }

    private CouncilRegistryAdapter.PersonContext ctx() {
        return new CouncilRegistryAdapter.PersonContext(tenantId, "Tariro", "Moyo");
    }

    @Test
    void authorityIdentityMatch_returnsIdentityMatchWithAuthorityConfidence() {
        Harness h = harness(repoReturningRegistration());
        h.server().expect(requestTo(ENDPOINT)).andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"matched\":true,\"identityMatch\":true,\"confidence\":0.95,\"recordRef\":\"REC-1\"}",
                        MediaType.APPLICATION_JSON));

        var outcome = h.adapter().verify("MDPCZ", "EC-12345", ctx());

        assertThat(outcome.result()).isEqualTo(CouncilRegistryAdapter.Result.IDENTITY_MATCH);
        assertThat(outcome.confidence()).isEqualByComparingTo("0.95"); // authority's score, not fabricated
        assertThat(outcome.matchedRef()).isEqualTo("REC-1");
        assertThat(outcome.reachable()).isTrue();
        h.server().verify();
    }

    @Test
    void authorityRegistryMatchOnly_returnsRegistryMatch() {
        Harness h = harness(repoReturningRegistration());
        h.server().expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(
                        "{\"matched\":true,\"identityMatch\":false,\"confidence\":0.7,\"recordRef\":\"REC-2\"}",
                        MediaType.APPLICATION_JSON));

        var outcome = h.adapter().verify("MDPCZ", "EC-12345", ctx());

        assertThat(outcome.result()).isEqualTo(CouncilRegistryAdapter.Result.REGISTRY_MATCH);
        assertThat(outcome.confidence()).isEqualByComparingTo("0.7");
    }

    @Test
    void authorityNoMatch_returnsNoMatch_notAPass() {
        Harness h = harness(repoReturningRegistration());
        h.server().expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"matched\":false}", MediaType.APPLICATION_JSON));

        var outcome = h.adapter().verify("MDPCZ", "EC-99999", ctx());

        assertThat(outcome.result()).isEqualTo(CouncilRegistryAdapter.Result.NO_MATCH);
        assertThat(outcome.isDefinitiveMatch()).isFalse();
        assertThat(outcome.confidence()).isNull();
    }

    @Test
    void authorityFormatInvalid_returnsFormatInvalid() {
        Harness h = harness(repoReturningRegistration());
        h.server().expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"matched\":false,\"formatInvalid\":true}",
                        MediaType.APPLICATION_JSON));

        var outcome = h.adapter().verify("MDPCZ", "??", ctx());

        assertThat(outcome.result()).isEqualTo(CouncilRegistryAdapter.Result.FORMAT_INVALID);
    }

    @Test
    void transportFailure_returnsUnreachable_neverAPass() {
        Harness h = harness(repoReturningRegistration());
        h.server().expect(requestTo(ENDPOINT))
                .andRespond(withException(new IOException("connection timed out")));

        var outcome = h.adapter().verify("MDPCZ", "EC-12345", ctx());

        // Honest degraded outcome — never a fabricated match/PASS.
        assertThat(outcome.result()).isEqualTo(CouncilRegistryAdapter.Result.UNREACHABLE);
        assertThat(outcome.reachable()).isFalse();
        assertThat(outcome.isDefinitiveMatch()).isFalse();
        assertThat(outcome.confidence()).isNull();
    }

    @Test
    void noEnabledRegistration_returnsNotConfigured() {
        CouncilAdapterRegistrationRepository repo = mock(CouncilAdapterRegistrationRepository.class);
        when(repo.findFirstByTenantIdAndCouncilCodeIgnoreCaseAndEnabledTrue(eq(tenantId), any()))
                .thenReturn(Optional.empty());
        Harness h = harness(repo);

        var outcome = h.adapter().verify("MDPCZ", "EC-12345", ctx());

        assertThat(outcome.result()).isEqualTo(CouncilRegistryAdapter.Result.NOT_CONFIGURED);
        h.server().verify(); // no HTTP call made
    }
}
