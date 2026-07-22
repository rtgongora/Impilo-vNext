package zw.gov.mohcc.impilo.experience.telemedicine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TM-B20 contract test: the version helper is the frozen migration boundary between bare and
 * {@code .v1} telemedicine event names. Locks the round-trip so consumer tolerance and emitter
 * stamping stay inverse and idempotent.
 */
class TelemedicineEventVersionTest {

    @Test
    void withV1_stamps_unversioned_telemedicine_events() {
        assertEquals("telemedicine.session.completed.v1",
                TelemedicineEventVersion.withV1("telemedicine.session.completed"));
        assertEquals("telemedicine.communication.reminder_sent.v1",
                TelemedicineEventVersion.withV1("telemedicine.communication.reminder_sent"));
    }

    @Test
    void withV1_is_idempotent() {
        assertEquals("telemedicine.session.completed.v1",
                TelemedicineEventVersion.withV1("telemedicine.session.completed.v1"));
    }

    @Test
    void withV1_leaves_legacy_upper_snake_and_foreign_events_untouched() {
        // Legacy exact-routed events (costa/tuso consume these) must NOT be suffixed.
        assertEquals("TELECONSULT_COMPLETED", TelemedicineEventVersion.withV1("TELECONSULT_COMPLETED"));
        assertEquals("POOL_MATERIALIZED", TelemedicineEventVersion.withV1("POOL_MATERIALIZED"));
        assertEquals("clinical.encounter.finalized", TelemedicineEventVersion.withV1("clinical.encounter.finalized"));
        assertNull(TelemedicineEventVersion.withV1(null));
    }

    @Test
    void strip_removes_version_and_tolerates_both_forms() {
        assertEquals("telemedicine.session.completed",
                TelemedicineEventVersion.strip("telemedicine.session.completed.v1"));
        assertEquals("telemedicine.session.completed",
                TelemedicineEventVersion.strip("telemedicine.session.completed.v2"));
        // Bare names pass through unchanged — the consumer accepts both during the window.
        assertEquals("telemedicine.session.completed",
                TelemedicineEventVersion.strip("telemedicine.session.completed"));
        // A trailing segment that only looks version-ish must not be mistaken for a version.
        assertEquals("telemedicine.session.vitals",
                TelemedicineEventVersion.strip("telemedicine.session.vitals"));
    }

    @Test
    void strip_and_withV1_round_trip() {
        String bare = "telemedicine.session.escalated";
        assertEquals(bare, TelemedicineEventVersion.strip(TelemedicineEventVersion.withV1(bare)));
    }
}
