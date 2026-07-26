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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RB-1: ending a regulatory appointment must revoke that person's org-scoped work sessions.
 *
 * <p>Before this consumer existed the {@code ended} event was published to a topic no service
 * subscribed to, so a revoked regulator kept working until their token lapsed. These cases pin the
 * envelope shape, because the failure they guard against is silent: a consumer reading the wrong
 * field name compiles, runs, and simply never matches.</p>
 */
@ExtendWith(MockitoExtension.class)
class RegulatoryAppointmentEndedTokenConsumerTest {

    @Mock private ScopedTokenRepository scopedTokenRepository;

    private RegulatoryAppointmentEndedTokenConsumer consumer;
    private final UUID organisationId = UUID.randomUUID();
    private final String healthId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        consumer = new RegulatoryAppointmentEndedTokenConsumer(new ObjectMapper(), scopedTokenRepository);
    }

    private String envelope(String eventType, String status, String person, String org) {
        return String.format(
                "{\"eventType\":\"%s\",\"aggregateType\":\"REGULATORY_APPOINTMENT\","
                        + "\"aggregateId\":\"appt-1\",\"payload\":{"
                        + "\"appointmentId\":\"appt-1\",\"personHealthId\":\"%s\","
                        + "\"organizationId\":\"%s\",\"roleCode\":\"REGISTRAR\",\"status\":\"%s\"}}",
                eventType, person, org, status);
    }

    // ── the teardown that did not happen ────────────────────────────────────

    @Test
    void endedAppointmentRevokesOrgScopedTokens() {
        consumer.onOrgRegistryEvent(envelope("ended", "ENDED", healthId, organisationId.toString()));

        verify(scopedTokenRepository).revokeWorkContextForActorAtOrganisation(
                eq(healthId), eq(organisationId.toString()), any(Instant.class));
    }

    @Test
    void revokedAppointmentAlsoTearsDown() {
        consumer.onOrgRegistryEvent(envelope("ended", "REVOKED", healthId, organisationId.toString()));

        verify(scopedTokenRepository).revokeWorkContextForActorAtOrganisation(
                eq(healthId), eq(organisationId.toString()), any(Instant.class));
    }

    /** The expiry sweep emits the same event; a lapse must close the session exactly as a revocation does. */
    @Test
    void expirySweepEndingAlsoTearsDown() {
        consumer.onOrgRegistryEvent(String.format(
                "{\"eventType\":\"ended\",\"aggregateType\":\"REGULATORY_APPOINTMENT\",\"payload\":{"
                        + "\"appointmentId\":\"appt-1\",\"personHealthId\":\"%s\",\"organizationId\":\"%s\","
                        + "\"status\":\"ENDED\",\"endReason\":\"EXPIRED\"}}",
                healthId, organisationId));

        verify(scopedTokenRepository).revokeWorkContextForActorAtOrganisation(
                eq(healthId), eq(organisationId.toString()), any(Instant.class));
    }

    // ── the envelope contract (the silent-failure guard) ────────────────────

    /**
     * org-registry's publisher writes camelCase {@code eventType}/{@code aggregateType}; vashandi's
     * writes snake_case {@code event_type}. Copying the sibling consumer's field names verbatim
     * would produce a consumer that runs forever and never fires.
     */
    @Test
    void snakeCaseEnvelopeIsNotWhatThisTopicSends() {
        consumer.onOrgRegistryEvent(String.format(
                "{\"event_type\":\"ended\",\"aggregate_type\":\"REGULATORY_APPOINTMENT\",\"payload\":{"
                        + "\"personHealthId\":\"%s\",\"organizationId\":\"%s\",\"status\":\"ENDED\"}}",
                healthId, organisationId));

        verify(scopedTokenRepository, never())
                .revokeWorkContextForActorAtOrganisation(any(), any(), any());
    }

    // ── scoping and safety ──────────────────────────────────────────────────

    /** Other aggregates share this topic (organisations, invitations, affiliations). */
    @Test
    void otherAggregateTypesOnTheSameTopicAreIgnored() {
        consumer.onOrgRegistryEvent(String.format(
                "{\"eventType\":\"ended\",\"aggregateType\":\"ORGANIZATION\",\"payload\":{"
                        + "\"personHealthId\":\"%s\",\"organizationId\":\"%s\",\"status\":\"ENDED\"}}",
                healthId, organisationId));

        verify(scopedTokenRepository, never())
                .revokeWorkContextForActorAtOrganisation(any(), any(), any());
    }

    @Test
    void createdAndVerifiedEventsDoNotRevoke() {
        consumer.onOrgRegistryEvent(envelope("created", "PENDING_VERIFICATION", healthId, organisationId.toString()));
        consumer.onOrgRegistryEvent(envelope("verified", "ACTIVE", healthId, organisationId.toString()));

        verify(scopedTokenRepository, never())
                .revokeWorkContextForActorAtOrganisation(any(), any(), any());
    }

    /** Never revoke on a guess — without both anchors we cannot say whose session, at which regulator. */
    @Test
    void missingAnchorsSkipTeardown() {
        consumer.onOrgRegistryEvent(envelope("ended", "ENDED", "", organisationId.toString()));
        consumer.onOrgRegistryEvent(envelope("ended", "ENDED", healthId, ""));

        verify(scopedTokenRepository, never())
                .revokeWorkContextForActorAtOrganisation(any(), any(), any());
    }

    @Test
    void malformedEventIsSwallowed() {
        consumer.onOrgRegistryEvent("not json");
        consumer.onOrgRegistryEvent("");
        consumer.onOrgRegistryEvent(null);

        verify(scopedTokenRepository, never())
                .revokeWorkContextForActorAtOrganisation(any(), any(), any());
    }
}
