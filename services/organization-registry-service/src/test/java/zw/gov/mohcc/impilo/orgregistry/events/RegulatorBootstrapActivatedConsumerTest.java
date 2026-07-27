package zw.gov.mohcc.impilo.orgregistry.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import zw.gov.mohcc.impilo.orgregistry.core.RegulatoryAppointmentService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RB-3b: the org-registry side of the bootstrap handshake — turn an approved
 * {@code regulator_bootstrap.activated} event into an ACTIVE founding appointment.
 *
 * <p>The cases pin the two things that would fail silently: routing (only OUR event, from a topic
 * shared with the adjudication consumer, may act) and the "always acknowledge" discipline that keeps
 * a malformed event from wedging the loop while an idempotent activation makes a redelivery safe.</p>
 */
@ExtendWith(MockitoExtension.class)
class RegulatorBootstrapActivatedConsumerTest {

    @Mock private RegulatoryAppointmentService appointmentService;
    @Mock private Acknowledgment acknowledgment;

    private RegulatorBootstrapActivatedConsumer consumer;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new RegulatorBootstrapActivatedConsumer(appointmentService, new ObjectMapper());
    }

    private String event(String eventType, String org, String person, String role) {
        // A null field is emitted as JSON null, not the literal string "null" — String.format("%s",
        // null) would produce "null" and defeat the missing-field routing guard under test.
        return "{\"eventType\":\"" + eventType + "\",\"tenantId\":\"" + tenantId + "\",\"payload\":{"
                + "\"organizationId\":" + json(org) + ","
                + "\"personHealthId\":" + json(person) + ","
                + "\"roleCode\":" + json(role) + ","
                + "\"evidenceRef\":\"gazette-77\",\"bootstrapRequestId\":\"RBR-1\"}}";
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    // ── the activation that grants the trust root ───────────────────────────

    @Test
    void activatesTheFoundingAppointmentAndAcks() throws Exception {
        consumer.onGovernanceEvent(
                event("impilo.governance.regulator_bootstrap.activated",
                        orgId.toString(), "person-1", "FOUNDING_REGULATOR_ADMINISTRATOR"),
                acknowledgment);

        verify(appointmentService).activateFoundingAppointment(
                eq(tenantId), eq(orgId), eq("person-1"), eq("FOUNDING_REGULATOR_ADMINISTRATOR"),
                eq("gazette-77"), eq("PLATFORM_BOOTSTRAP:RBR-1"));
        verify(acknowledgment).acknowledge();
    }

    // ── routing: the topic is shared with the adjudication consumer ─────────

    @Test
    void ignoresOtherEventTypesOnTheSharedTopic() throws Exception {
        consumer.onGovernanceEvent(
                event("impilo.governance.adjudication.decision.recorded",
                        orgId.toString(), "person-1", "FOUNDING_REGULATOR_ADMINISTRATOR"),
                acknowledgment);

        verify(appointmentService, never())
                .activateFoundingAppointment(any(), any(), any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    // ── never wedge the loop; never act on a guess ──────────────────────────

    @Test
    void missingRoutingFieldsAreDroppedAndAcked() throws Exception {
        consumer.onGovernanceEvent(
                event("impilo.governance.regulator_bootstrap.activated",
                        orgId.toString(), null, "FOUNDING_REGULATOR_ADMINISTRATOR"),
                acknowledgment);

        verify(appointmentService, never())
                .activateFoundingAppointment(any(), any(), any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    /**
     * A domain guard rejecting the activation (the seat is no longer empty) must be logged and
     * acked, not left to crash the loop — the redelivery would fail identically, and an idempotent
     * activation makes a genuine retry safe.
     */
    @Test
    void aRejectedActivationIsSwallowedAndAcked() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("BOOTSTRAP_ROLE_NOT_AVAILABLE"))
                .when(appointmentService).activateFoundingAppointment(
                        any(), any(), any(), any(), any(), any());

        consumer.onGovernanceEvent(
                event("impilo.governance.regulator_bootstrap.activated",
                        orgId.toString(), "person-1", "FOUNDING_REGULATOR_ADMINISTRATOR"),
                acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    void malformedEventIsAcked() throws Exception {
        consumer.onGovernanceEvent("not json", acknowledgment);
        verify(appointmentService, never())
                .activateFoundingAppointment(any(), any(), any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }
}
