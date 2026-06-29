package zw.gov.mohcc.impilo.khuluma.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.khuluma.core.EscalationService.NewEscalation;
import zw.gov.mohcc.impilo.khuluma.domain.EscalationEntity;
import zw.gov.mohcc.impilo.khuluma.domain.SlaPolicyEntity;
import zw.gov.mohcc.impilo.khuluma.repository.EscalationRepository;
import zw.gov.mohcc.impilo.khuluma.repository.SlaPolicyRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalationServiceTest {

    private static final UUID T = UUID.randomUUID();

    @Mock private EscalationRepository escalations;
    @Mock private SlaPolicyRepository policies;
    @Mock private OutboxAppender outbox;

    private EscalationService service() {
        return new EscalationService(escalations, policies, outbox);
    }

    /** Simulate JPA: save assigns a generated id (so post-save emit has an aggregate id, as in prod). */
    @BeforeEach
    void stubSave() {
        when(escalations.save(any())).thenAnswer(inv -> {
            EscalationEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                var f = EscalationEntity.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(e, UUID.randomUUID());
            }
            return e;
        });
    }

    private EscalationEntity open(String status) {
        EscalationEntity e = new EscalationEntity();
        e.setTenantId(T);
        e.setStatus(status);
        e.setPriority("MEDIUM");
        return e;
    }

    @Test
    void open_uses_default_sla_targets_and_emits() {
        when(policies.findByTenantIdAndPriorityAndActiveTrue(T, "URGENT")).thenReturn(Optional.empty());

        EscalationEntity e = service().open(T, new NewEscalation(UUID.randomUUID(), "patient distress", "urgent"));

        assertThat(e.getStatus()).isEqualTo("OPEN");
        assertThat(e.getPriority()).isEqualTo("URGENT");
        // URGENT defaults: first-response 5m, resolution 30m → delta 25m (from a single 'now').
        assertThat(Duration.between(e.getFirstResponseDueAt(), e.getResolutionDueAt()).toMinutes()).isEqualTo(25);
        verify(outbox).append(eqEvent("opened"), anyString(), anyString(), any(), anyString(),
                anyString(), anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void open_honors_a_tenant_sla_policy_override() {
        SlaPolicyEntity p = new SlaPolicyEntity();
        p.setFirstResponseMinutes(2);
        p.setResolutionMinutes(10);
        when(policies.findByTenantIdAndPriorityAndActiveTrue(T, "HIGH")).thenReturn(Optional.of(p));

        EscalationEntity e = service().open(T, new NewEscalation(null, "r", "high"));

        assertThat(Duration.between(e.getFirstResponseDueAt(), e.getResolutionDueAt()).toMinutes()).isEqualTo(8);
    }

    @Test
    void accept_stops_first_response_clock() {
        UUID id = UUID.randomUUID();
        when(escalations.findByIdAndTenantId(id, T)).thenReturn(Optional.of(open("ASSIGNED")));

        EscalationEntity e = service().accept(T, id, "agent-1");

        assertThat(e.getStatus()).isEqualTo("ACCEPTED");
        assertThat(e.getFirstRespondedAt()).isNotNull();
        assertThat(e.getAssignedTo()).isEqualTo("agent-1");
    }

    @Test
    void escalate_bumps_tier_and_priority() {
        UUID id = UUID.randomUUID();
        when(escalations.findByIdAndTenantId(id, T)).thenReturn(Optional.of(open("ACCEPTED")));

        EscalationEntity e = service().escalate(T, id, "still unresolved");

        assertThat(e.getTier()).isEqualTo(2);
        assertThat(e.getPriority()).isEqualTo("HIGH"); // MEDIUM → HIGH
    }

    @Test
    void resolve_then_illegal_reopen_is_rejected() {
        UUID id = UUID.randomUUID();
        EscalationEntity e = open("ACCEPTED");
        when(escalations.findByIdAndTenantId(id, T)).thenReturn(Optional.of(e));

        service().resolve(T, id, "addressed");
        assertThat(e.getStatus()).isEqualTo("RESOLVED");

        assertThatThrownBy(() -> service().escalate(T, id, "again"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("terminal");
    }

    @Test
    void sla_sweep_marks_first_response_and_resolution_breaches() {
        EscalationEntity fr = open("OPEN");
        EscalationEntity res = open("ACCEPTED");
        when(escalations.findFirstResponseBreaches(any())).thenReturn(List.of(fr));
        when(escalations.findResolutionBreaches(any())).thenReturn(List.of(res));

        service().checkSlaBreaches();

        assertThat(fr.isFirstResponseBreached()).isTrue();
        assertThat(res.isResolutionBreached()).isTrue();
        assertThat(res.getStatus()).isEqualTo("BREACHED");
        // one event per breach.
        verify(outbox, times(2)).append(anyString(), anyString(), anyString(), any(), anyString(),
                anyString(), anyString(), any(), anyString(), anyString(), anyString());
    }

    // matcher helper: assert the event-type arg contains the lifecycle keyword.
    private static String eqEvent(String keyword) {
        return org.mockito.ArgumentMatchers.contains(keyword);
    }
}
