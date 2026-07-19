package zw.gov.mohcc.impilo.tshepo.identity.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.ScopedTokenRepository;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * W5/D-P7: an ended assignment revokes the actor's WORK_CONTEXT tokens at THAT
 * facility only; missing anchors skip; unrelated assignment events do nothing.
 */
@ExtendWith(MockitoExtension.class)
class AssignmentEndedTokenConsumerTest {

    @Mock private ScopedTokenRepository scopedTokenRepository;

    private AssignmentEndedTokenConsumer consumer;
    private final UUID facilityId = UUID.randomUUID();
    private final String healthId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        consumer = new AssignmentEndedTokenConsumer(new ObjectMapper(), scopedTokenRepository);
    }

    @Test
    void endedAssignmentRevokesFacilityScopedTokens() {
        consumer.onAssignmentEvent(String.format(
                "{\"event_type\":\"impilo.vashandi.assignment.ended.v1\",\"payload\":{"
                        + "\"assignmentId\":\"a-1\",\"impiloHealthId\":\"%s\","
                        + "\"facilityId\":\"%s\",\"status\":\"ended\"}}",
                healthId, facilityId));

        verify(scopedTokenRepository).revokeWorkContextForActorAtFacility(
                eq(healthId), eq(facilityId.toString()), any(Instant.class));
    }

    @Test
    void missingPersonAnchorSkipsTeardown() {
        consumer.onAssignmentEvent(String.format(
                "{\"event_type\":\"impilo.vashandi.assignment.ended.v1\",\"payload\":{"
                        + "\"assignmentId\":\"a-1\",\"impiloHealthId\":\"\",\"facilityId\":\"%s\","
                        + "\"status\":\"ended\"}}",
                facilityId));

        verify(scopedTokenRepository, never()).revokeWorkContextForActorAtFacility(any(), any(), any());
    }

    @Test
    void nonEndEventsAreIgnored() {
        consumer.onAssignmentEvent(String.format(
                "{\"event_type\":\"impilo.vashandi.assignment.created.v1\",\"payload\":{"
                        + "\"assignmentId\":\"a-1\",\"impiloHealthId\":\"%s\",\"facilityId\":\"%s\","
                        + "\"status\":\"active\"}}",
                healthId, facilityId));

        verify(scopedTokenRepository, never()).revokeWorkContextForActorAtFacility(any(), any(), any());
    }

    @Test
    void malformedEventIsSwallowed() {
        consumer.onAssignmentEvent("not json");
        verify(scopedTokenRepository, never()).revokeWorkContextForActorAtFacility(any(), any(), any());
    }
}
