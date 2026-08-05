package zw.gov.mohcc.impilo.experience.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Provider capability comes from the registry or it does not exist.
 *
 * <p>The doctrine, from {@code CLAUDE.md}: professional capacity is activated by a Provider ID —
 * reached either through a Health ID that carries one, or by the Provider ID itself. Never by a
 * name, and never by a Keycloak role label. Until now
 * {@code AuthSessionController.determineActorType} granted it on
 * {@code roles.contains("CLINICIAN")} alone.</p>
 */
class ProviderCapabilityTest {

    private static final ObjectMapper M = new ObjectMapper();

    private SessionExperienceService serviceReturning(String json) throws Exception {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        when(varapi.getProviderByHealthId(anyString()))
                .thenReturn(json == null ? null : M.readTree(json));
        return new SessionExperienceService(varapi, null, null, null, M);
    }

    private SessionExperienceService serviceThrowing() {
        VarapiServiceClient varapi = mock(VarapiServiceClient.class);
        when(varapi.getProviderByHealthId(anyString()))
                .thenThrow(new RuntimeException("varapi unreachable"));
        return new SessionExperienceService(varapi, null, null, null, M);
    }

    @Test
    @DisplayName("a Health ID carrying a valid Provider ID yields PROVIDER")
    void healthIdWithProviderIdIsProvider() throws Exception {
        var capability = serviceReturning(
                "{\"providerId\":\"PRV-001\",\"status\":\"active\",\"licenceValid\":true}")
                .resolveProviderCapability("health-1");

        assertThat(capability.isProvider()).isTrue();
        assertThat(capability.providerId()).isEqualTo("PRV-001");
        assertThat(capability.actorTypeOrNull()).isEqualTo("PROVIDER");
    }

    @Test
    @DisplayName("no Provider ID attached means NOT a provider, whatever roles claim")
    void noProviderIdIsNotAProvider() throws Exception {
        var capability = serviceReturning("{\"status\":\"none\"}")
                .resolveProviderCapability("health-2");

        assertThat(capability.isProvider()).isFalse();
        assertThat(capability.actorTypeOrNull()).isEqualTo("CITIZEN");
    }

    @Test
    @DisplayName("a suspended or revoked provider is NOT granted capacity")
    void ineligibleStatusIsNotAProvider() throws Exception {
        for (String status : new String[] {"suspended", "revoked", "expired"}) {
            var capability = serviceReturning(
                    "{\"providerId\":\"PRV-9\",\"status\":\"" + status + "\"}")
                    .resolveProviderCapability("health-3");
            assertThat(capability.isProvider())
                    .as("status %s must not confer professional capacity", status)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("AN OUTAGE IS NOT AN ANSWER — the case enforcement turns on")
    void registryUnreachableIsUnavailableNotAbsence() {
        // fetchLinkedIds swallows every exception into an empty map, so an outage and "not a
        // provider" are byte-identical there. Enforcing on that would strip clinical access from
        // every provider at once during a VARAPI blip, and report it as though each had failed a
        // check. This is the distinction that makes enforcement safe.
        var capability = serviceThrowing().resolveProviderCapability("health-4");

        assertThat(capability.resolution())
                .isEqualTo(ProviderCapability.Resolution.UNAVAILABLE);
        assertThat(capability.resolution())
                .isNotEqualTo(ProviderCapability.Resolution.NOT_A_PROVIDER);
        assertThat(capability.isProvider())
                .as("still fails closed — unavailable never grants")
                .isFalse();
        assertThat(capability.actorTypeOrNull())
                .as("must NOT claim CITIZEN: that is an assertion about the person "
                        + "derived from a fact about the network")
                .isNull();
    }

    @Test
    @DisplayName("an empty registry answer is an absence, not an outage")
    void nullNodeIsAbsence() throws Exception {
        var capability = serviceReturning(null).resolveProviderCapability("health-5");

        assertThat(capability.resolution())
                .isEqualTo(ProviderCapability.Resolution.NOT_A_PROVIDER);
    }

    @Test
    @DisplayName("a blank actor resolves to absence without calling the registry")
    void blankActorIsAbsence() throws Exception {
        assertThat(serviceReturning("{}").resolveProviderCapability("").resolution())
                .isEqualTo(ProviderCapability.Resolution.NOT_A_PROVIDER);
    }

    @Test
    @DisplayName("PROVIDER is reachable — a resolver that never grants would pass everything above")
    void resolverCanActuallyGrant() throws Exception {
        assertThat(serviceReturning(
                "{\"providerId\":\"PRV-2\",\"status\":\"verified\",\"licenceValid\":true}")
                .resolveProviderCapability("health-6").isProvider()).isTrue();
    }
}
