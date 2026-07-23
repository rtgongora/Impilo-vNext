package zw.gov.mohcc.impilo.telemonitoring.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Outbox topic-routing convention (epic contract: telemonitoring.plan.{action}.v1). */
class OutboxRoutingTest {

    @Test
    void planLifecycleEventsPublishOnTheirOwnTopicName() {
        for (String action : new String[]{"created", "activated", "amended", "suspended", "completed", "cancelled"}) {
            String eventType = TelemonitoringEventEmitter.planEventType(action);
            assertThat(eventType).isEqualTo("telemonitoring.plan." + action + ".v1");
            assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                    TelemonitoringEventEmitter.AGGREGATE_MONITORING_PLAN, eventType))
                    .isEqualTo(eventType);
        }
    }

    @Test
    void nonPlanAggregatesFallBackToTheDefaultTopic() {
        assertThat(TelemonitoringOutboxPublisher.resolveTopic("SOMETHING_ELSE",
                "telemonitoring.plan.created.v1"))
                .isEqualTo("telemonitoring.events");
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_MONITORING_PLAN, "weird.event"))
                .isEqualTo("telemonitoring.events");
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_MONITORING_PLAN, null))
                .isEqualTo("telemonitoring.events");
    }
}
