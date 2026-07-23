package zw.gov.mohcc.impilo.telemonitoring.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OF-B25 — the new reading/observation v1 event families publish on topics named by
 * their own event type (same convention as plan/programme/device); anything malformed
 * falls back to the default stream.
 */
class ReadingObservationRoutingTest {

    @Test
    void readingEventsRouteToTheirOwnTopic() {
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_TELEMETRY_READING,
                "telemonitoring.reading.quarantined.v1"))
                .isEqualTo("telemonitoring.reading.quarantined.v1");
    }

    @Test
    void observationEventsRouteToTheirOwnTopics() {
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_TELEMETRY_READING,
                "telemonitoring.observation.recorded.v1"))
                .isEqualTo("telemonitoring.observation.recorded.v1");
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_TELEMETRY_READING,
                "telemonitoring.observation.write_failed.v1"))
                .isEqualTo("telemonitoring.observation.write_failed.v1");
    }

    @Test
    void unversionedOrForeignEventsFallBackToDefaultStream() {
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_TELEMETRY_READING, "telemonitoring.reading.quarantined"))
                .isEqualTo(TelemonitoringOutboxPublisher.DEFAULT_TOPIC);
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                "SOMETHING_ELSE", "telemonitoring.reading.quarantined.v1"))
                .isEqualTo(TelemonitoringOutboxPublisher.DEFAULT_TOPIC);
    }
}
