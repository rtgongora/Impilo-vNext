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
}
