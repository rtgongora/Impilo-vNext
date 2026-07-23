package zw.gov.mohcc.impilo.telemonitoring.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Outbox topic-routing convention (epic contract: telemonitoring.plan.{action}.v1). */
class OutboxRoutingTest {

    @Test
    void planLifecycleEventsPublishOnTheirOwnTopicName() {
        for (String action : new String[]{"created", "activated", "amended", "suspended", "completed", "cancelled",
                "task_requested", "review_due", "review_recorded", "consent_updated"}) {
            String eventType = TelemonitoringEventEmitter.planEventType(action);
            assertThat(eventType).isEqualTo("telemonitoring.plan." + action + ".v1");
            assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                    TelemonitoringEventEmitter.AGGREGATE_MONITORING_PLAN, eventType))
                    .isEqualTo(eventType);
        }
    }

    @Test
    void programmeGovernanceEventsPublishOnTheirOwnTopicName() {
        for (String action : new String[]{"created", "updated", "retired"}) {
            String eventType = TelemonitoringEventEmitter.programmeEventType(action);
            assertThat(eventType).isEqualTo("telemonitoring.programme." + action + ".v1");
            assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                    TelemonitoringEventEmitter.AGGREGATE_MONITORING_PROGRAMME, eventType))
                    .isEqualTo(eventType);
        }
        // Aggregate/type must agree — a programme prefix on a plan aggregate is not routed.
        assertThat(TelemonitoringOutboxPublisher.resolveTopic(
                TelemonitoringEventEmitter.AGGREGATE_MONITORING_PLAN,
                "telemonitoring.programme.created.v1"))
                .isEqualTo("telemonitoring.events");
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
