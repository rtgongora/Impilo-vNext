package zw.gov.mohcc.impilo.pct.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxPublisherRouteTest {

    @Test
    void admission_approved_routes_to_admission_topic() {
        // The PCT<->inpatient handshake depends on ADMISSION_APPROVED landing on pct.admission.updated,
        // which inpatient-service's consumer listens to.
        assertEquals("pct.admission.updated", OutboxPublisher.routeTopic("ADMISSION_APPROVED"));
    }

    @Test
    void existing_admission_events_still_route_to_admission_topic() {
        assertEquals("pct.admission.updated", OutboxPublisher.routeTopic("ADMISSION_REQUESTED"));
        assertEquals("pct.admission.updated", OutboxPublisher.routeTopic("PATIENT_ADMITTED"));
    }

    @Test
    void unknown_event_falls_back_to_default_topic() {
        assertEquals("pct.events", OutboxPublisher.routeTopic("SOMETHING_NEW"));
    }

    @Test
    void teleconsult_completed_routes_to_value_topic() {
        // telemed->value: COSTA/L4 consumes the completed teleconsult to raise a charge.
        assertEquals("clinical.teleconsult.value", OutboxPublisher.routeTopic("TELECONSULT_COMPLETED"));
    }

    @Test
    void telemedicine_session_events_route_to_lifecycle_topic() {
        assertEquals("clinical.teleconsult.lifecycle", OutboxPublisher.routeTopic("telemedicine.session.completed"));
        assertEquals("clinical.teleconsult.lifecycle", OutboxPublisher.routeTopic("telemedicine.session.routed"));
    }

    @Test
    void versioned_v1_session_events_still_route_to_lifecycle_topic() {
        // TM-B20: prefix routing (startsWith "telemedicine.session.") must survive the .v1 suffix,
        // so the versioned events land on the same topic as their legacy bare names.
        assertEquals("clinical.teleconsult.lifecycle", OutboxPublisher.routeTopic("telemedicine.session.completed.v1"));
        assertEquals("clinical.teleconsult.lifecycle", OutboxPublisher.routeTopic("telemedicine.session.expired.v1"));
    }

    @org.junit.jupiter.api.Test
    void observationsGetTheirOwnTopicSoTheShrBridgeCanSubscribeToThem() {
        // Caught by deploying: the event fell through to the pct.events catch-all, BUTANO was
        // listening on pct.observation.recorded, and the observation reached the shared health
        // record never. The route is the contract between the two services.
        assertEquals("pct.observation.recorded",
                OutboxPublisher.routeTopic("pct.observation.recorded"));
    }

    @org.junit.jupiter.api.Test
    void imamLifecycleEventsRouteToTheirOwnTopics() {
        assertEquals("pct.imam.episode.updated", OutboxPublisher.routeTopic("IMAM_EPISODE_ENROLLED"));
        assertEquals("pct.imam.episode.updated", OutboxPublisher.routeTopic("IMAM_EPISODE_CLOSED"));
        assertEquals("pct.imam.visit.recorded", OutboxPublisher.routeTopic("IMAM_VISIT_RECORDED"));
    }

    @org.junit.jupiter.api.Test
    void imamTracingHasATopicOfItsOwnBecauseADifferentTeamActsOnIt() {
        // A defaulter trace is a community health worker's home visit, not the clinic's next
        // review. Left on the episode topic it would arrive mixed in with routine updates.
        assertEquals("pct.imam.tracing.required", OutboxPublisher.routeTopic("IMAM_TRACING_REQUIRED"));
    }

    @org.junit.jupiter.api.Test
    void aCriticalResultReachesTheTopicRitoAndNotificationSubscribeTo() {
        // The estate's only ED safety event. EdDiagnosticsService emitted it from the day the ED
        // diagnostics lane shipped, but with no case here it rode the pct.events catch-all: the
        // publish succeeded, the outbox row was marked published, every log said the send worked,
        // and no consumer heard it. A critical result nobody is told about is the exact failure the
        // acknowledge-and-act loop exists to prevent.
        assertEquals("pct.emergency.critical_result",
                OutboxPublisher.routeTopic("pct.ed.critical_result"));
    }

    @org.junit.jupiter.api.Test
    void anUnroutedEventTypeStillLandsOnTheCatchAllRatherThanBeingDropped() {
        assertEquals("pct.events", OutboxPublisher.routeTopic("something.nobody.routed"));
    }
}
