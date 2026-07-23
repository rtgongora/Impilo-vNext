package zw.gov.mohcc.impilo.telemonitoring.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** OF-B24 — outbox topic routing for telemonitoring.device.{assigned,activated,returned}.v1. */
class DeviceOutboxRoutingTest {

    @Test
    void deviceAssignmentEventsPublishOnTheirOwnTopicName() {
        for (String action : new String[]{"assigned", "activated", "returned"}) {
            String eventType = TelemonitoringEventEmitter.deviceEventType(action);
            assertThat(eventType).isEqualTo("telemonitoring.device." + action + ".v1");
            assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                    TelemonitoringEventEmitter.AGGREGATE_DEVICE_ASSIGNMENT, eventType))
                    .isEqualTo(eventType);
        }
    }

    @Test
    void deviceRoutingDemandsBothAggregateAndVersionedShape() {
        // Wrong aggregate: a device-shaped type on another aggregate falls back.
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_MONITORING_PLAN,
                "telemonitoring.device.assigned.v1"))
                .isEqualTo("telemonitoring.events");
        // Unversioned event types never get their own topic (no unversioned additions).
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_DEVICE_ASSIGNMENT,
                "telemonitoring.device.assigned"))
                .isEqualTo("telemonitoring.events");
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_DEVICE_ASSIGNMENT, null))
                .isEqualTo("telemonitoring.events");
    }

    @Test
    void planRoutingIsUntouchedByTheDeviceAddition() {
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_MONITORING_PLAN,
                "telemonitoring.plan.created.v1"))
                .isEqualTo("telemonitoring.plan.created.v1");
    }
}
