package zw.gov.mohcc.impilo.tshepo.identity.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.ScopedTokenRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * D-P3 token teardown: a provider lifecycle transition out of an active
 * practising state revokes every ACTIVE scoped token for the person anchor;
 * events without a person anchor never revoke anyone (no guessing), and
 * still-practising transitions revoke nothing.
 */
@ExtendWith(MockitoExtension.class)
class ProviderRevocationTokenConsumerTest {

    @Mock private ScopedTokenRepository scopedTokenRepository;

    private ProviderRevocationTokenConsumer consumer;
    private final UUID personHealthId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new ProviderRevocationTokenConsumer(new ObjectMapper(), scopedTokenRepository);
    }

    @Test
    void lapsedLifecycleWithPersonAnchorRevokesAllActorTokens() {
        consumer.onVarapiEvent(String.format(
                "{\"providerPublicId\":\"PROV1\",\"impiloHealthId\":\"%s\","
                        + "\"previousLifecycle\":\"LICENCED_ACTIVE\",\"newLifecycle\":\"LAPSED\","
                        + "\"licenceValidTo\":\"2026-06-30\"}",
                personHealthId));

        verify(scopedTokenRepository).revokeAllForActor(eq(personHealthId.toString()), any(Instant.class));
    }

    @Test
    void v11EnvelopeLapsedEventIsAlsoDetected() {
        consumer.onVarapiEvent(String.format(
                "{\"event_type\":\"varapi.provider.lapsed\",\"payload\":{"
                        + "\"providerPublicId\":\"PROV1\",\"impiloHealthId\":\"%s\","
                        + "\"newLifecycle\":\"LAPSED\"}}",
                personHealthId));

        verify(scopedTokenRepository).revokeAllForActor(eq(personHealthId.toString()), any(Instant.class));
    }

    @Test
    void revocationWithoutPersonAnchorNeverRevokesAnyone() {
        consumer.onVarapiEvent(
                "{\"providerPublicId\":\"PROV1\",\"impiloHealthId\":\"\","
                        + "\"previousLifecycle\":\"LICENCED_ACTIVE\",\"newLifecycle\":\"LAPSED\"}");

        verify(scopedTokenRepository, never()).revokeAllForActor(any(), any());
    }

    @Test
    void stillPractisingTransitionRevokesNothing() {
        consumer.onVarapiEvent(String.format(
                "{\"providerPublicId\":\"PROV1\",\"impiloHealthId\":\"%s\","
                        + "\"previousLifecycle\":\"LICENCED_ACTIVE\","
                        + "\"newLifecycle\":\"LICENCE_DUE_FOR_RENEWAL\"}",
                personHealthId));

        verify(scopedTokenRepository, never()).revokeAllForActor(any(), any());
    }

    @Test
    void malformedEventIsSwallowedNotThrown() {
        consumer.onVarapiEvent("not json at all");
        verify(scopedTokenRepository, never()).revokeAllForActor(any(), any());
    }

    @Test
    void detectionMirrorsPj15Semantics() {
        assertThat(ProviderRevocationTokenConsumer.isRevocation("varapi.provider.lapsed", null, null, "LAPSED")).isTrue();
        assertThat(ProviderRevocationTokenConsumer.isRevocation("", null, null, "SUSPENDED")).isTrue();
        assertThat(ProviderRevocationTokenConsumer.isRevocation("varapi.provider.status_changed", "SUSPENDED", null, null)).isTrue();
        assertThat(ProviderRevocationTokenConsumer.isRevocation("", null, null, "LICENCED_ACTIVE")).isFalse();
        assertThat(ProviderRevocationTokenConsumer.isRevocation("varapi.provider.claimed", null, null, null)).isFalse();
    }
}
