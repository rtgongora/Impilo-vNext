package zw.gov.mohcc.impilo.telemonitoring.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OF-B26 — outbox topic routing for {@code telemonitoring.alert.*.v1} events
 * (same convention as plan/programme/device/reading rows: the topic IS the event type).
 */
class AlertEventRoutingTest {

    @Test
    void alertEventsRouteToTheirOwnTopic() {
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_ALERT_EPISODE, "telemonitoring.alert.opened.v1"))
                .isEqualTo("telemonitoring.alert.opened.v1");
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_ALERT_EPISODE, "telemonitoring.alert.acknowledged.v1"))
                .isEqualTo("telemonitoring.alert.acknowledged.v1");
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_ALERT_EPISODE, "telemonitoring.alert.escalated.v1"))
                .isEqualTo("telemonitoring.alert.escalated.v1");
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_ALERT_EPISODE, "telemonitoring.alert.resolved.v1"))
                .isEqualTo("telemonitoring.alert.resolved.v1");
    }

    @Test
    void alertEventTypeBuilderMatchesTheContract() {
        assertThat(TelemonitoringEventEmitter.alertEventType("opened"))
                .isEqualTo("telemonitoring.alert.opened.v1");
    }

    @Test
    void malformedAlertRowsFallBackToTheDefaultTopic() {
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_ALERT_EPISODE, "some.other.event"))
                .isEqualTo(TelemonitoringOutboxPublisher.DEFAULT_TOPIC);
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                "SOMETHING_ELSE", "telemonitoring.alert.opened.v1"))
                .isEqualTo(TelemonitoringOutboxPublisher.DEFAULT_TOPIC);
    }
}
