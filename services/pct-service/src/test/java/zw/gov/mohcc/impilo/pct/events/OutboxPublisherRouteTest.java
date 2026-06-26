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
}
