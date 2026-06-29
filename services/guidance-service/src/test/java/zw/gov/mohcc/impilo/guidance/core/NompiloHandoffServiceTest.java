package zw.gov.mohcc.impilo.guidance.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.guidance.core.NompiloHandoffService.NewHandoff;
import zw.gov.mohcc.impilo.guidance.events.GuidanceOutboxAppender;
import zw.gov.mohcc.impilo.guidance.persistence.entity.NompiloHandoffEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.NompiloHandoffRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NompiloHandoffServiceTest {

    @Mock private NompiloHandoffRepository repository;
    @Mock private GuidanceOutboxAppender outbox;

    private NompiloHandoffService service() {
        return new NompiloHandoffService(repository, outbox);
    }

    private NompiloHandoffEntity withStatus(String status) {
        NompiloHandoffEntity h = new NompiloHandoffEntity();
        h.setTenantId("t1");
        h.setStatus(status);
        return h;
    }

    @Test
    void request_queues_and_emits_canonical_event_with_normalized_fields() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NompiloHandoffEntity h = service().request("t1",
                new NewHandoff("TX-1", "SUB-1", "ACT-1", "needs a human", "high", "call_centre", "flow-9", "{\"k\":1}"));

        assertThat(h.getStatus()).isEqualTo("QUEUED");
        assertThat(h.getPriority()).isEqualTo("HIGH");          // normalized from "high"
        assertThat(h.getDestination()).isEqualTo("CALL_CENTRE"); // normalized from "call_centre"
        verify(outbox).recordHandoffEvent(h, "core.nompilo.handoff.requested");
    }

    @Test
    void unknown_priority_or_destination_falls_back_safely() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NompiloHandoffEntity h = service().request("t1",
                new NewHandoff(null, null, "ACT", null, "NONSENSE", "BOGUS", null, null));

        assertThat(h.getPriority()).isEqualTo("MEDIUM");
        assertThat(h.getDestination()).isEqualTo("CARE_TEAM");
    }

    @Test
    void accept_transitions_queued_to_accepted_and_emits() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, "t1")).thenReturn(Optional.of(withStatus("QUEUED")));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NompiloHandoffEntity h = service().accept("t1", id, "agent-1");

        assertThat(h.getStatus()).isEqualTo("ACCEPTED");
        assertThat(h.getAssignedTo()).isEqualTo("agent-1");
        assertThat(h.getAcceptedAt()).isNotNull();
        verify(outbox).recordHandoffEvent(h, "guidance.nompilo.handoff.accepted");
    }

    @Test
    void escalate_then_close_walks_the_lifecycle() {
        UUID id = UUID.randomUUID();
        NompiloHandoffEntity h = withStatus("ACCEPTED");
        when(repository.findByIdAndTenantId(id, "t1")).thenReturn(Optional.of(h));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().escalate("t1", id, "no response");
        assertThat(h.getStatus()).isEqualTo("ESCALATED");
        verify(outbox).recordHandoffEvent(h, "guidance.nompilo.handoff.escalated");

        service().close("t1", id, "resolved by supervisor");
        assertThat(h.getStatus()).isEqualTo("CLOSED");
        assertThat(h.getClosedAt()).isNotNull();
        verify(outbox).recordHandoffEvent(h, "guidance.nompilo.handoff.closed");
    }

    @Test
    void illegal_transition_is_rejected() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, "t1")).thenReturn(Optional.of(withStatus("CLOSED")));

        assertThatThrownBy(() -> service().close("t1", id, "again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
        assertThatThrownBy(() -> service().accept("t1", id, "agent"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missing_handoff_is_a_clear_error() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, "t1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get("t1", id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
