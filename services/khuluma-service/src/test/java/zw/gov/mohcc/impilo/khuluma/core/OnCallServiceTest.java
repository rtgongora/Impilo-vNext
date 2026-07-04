package zw.gov.mohcc.impilo.khuluma.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.khuluma.domain.PresenceEntity;
import zw.gov.mohcc.impilo.khuluma.repository.PresenceRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnCallServiceTest {

    private static final UUID T = UUID.randomUUID();

    @Mock private PresenceRepository presence;

    private OnCallService service() {
        return new OnCallService(presence);
    }

    @Test
    void set_duty_upserts_and_normalizes() {
        when(presence.findByTenantIdAndActorId(T, "worker-1")).thenReturn(Optional.empty());
        when(presence.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PresenceEntity p = service().setDuty(T, "worker-1", "PROVIDER", "on_call");

        assertThat(p.getDutyStatus()).isEqualTo("ON_CALL");
        assertThat(p.getActorId()).isEqualTo("worker-1");
    }

    @Test
    void unknown_duty_status_is_rejected() {
        assertThatThrownBy(() -> service().setDuty(T, "w", "PROVIDER", "VACATION"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().setDuty(T, "w", "PROVIDER", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roster_returns_on_call_workers() {
        PresenceEntity p = new PresenceEntity();
        p.setActorId("worker-1");
        p.setDutyStatus("ON_CALL");
        when(presence.findByTenantIdAndDutyStatusOrderByUpdatedAtDesc(T, "ON_CALL")).thenReturn(List.of(p));

        assertThat(service().onCallRoster(T)).extracting(PresenceEntity::getActorId).containsExactly("worker-1");
    }

    @Test
    void set_duty_persists_normalized_specialty_and_blank_clears_it() {
        when(presence.findByTenantIdAndActorId(T, "worker-1")).thenReturn(Optional.empty());
        when(presence.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PresenceEntity p = service().setDuty(T, "worker-1", "PROVIDER", "ON_CALL", "cardiology");
        assertThat(p.getSpecialty()).isEqualTo("CARDIOLOGY");

        // null specialty leaves the stored value unchanged
        when(presence.findByTenantIdAndActorId(T, "worker-1")).thenReturn(Optional.of(p));
        assertThat(service().setDuty(T, "worker-1", "PROVIDER", "ON_CALL", null).getSpecialty())
                .isEqualTo("CARDIOLOGY");

        // blank specialty clears it
        assertThat(service().setDuty(T, "worker-1", "PROVIDER", "ON_CALL", " ").getSpecialty()).isNull();
    }

    @Test
    void roster_filters_by_specialty_case_insensitively() {
        PresenceEntity cardio = new PresenceEntity();
        cardio.setActorId("cardio-1");
        cardio.setDutyStatus("ON_CALL");
        cardio.setSpecialty("CARDIOLOGY");
        PresenceEntity derm = new PresenceEntity();
        derm.setActorId("derm-1");
        derm.setDutyStatus("ON_CALL");
        derm.setSpecialty("DERMATOLOGY");
        PresenceEntity generalist = new PresenceEntity();
        generalist.setActorId("gen-1");
        generalist.setDutyStatus("ON_CALL");
        when(presence.findByTenantIdAndDutyStatusOrderByUpdatedAtDesc(T, "ON_CALL"))
                .thenReturn(List.of(cardio, derm, generalist));

        assertThat(service().onCallRoster(T, "cardiology"))
                .extracting(PresenceEntity::getActorId).containsExactly("cardio-1");
        // no specialty filter → full roster (workers without specialty included)
        assertThat(service().onCallRoster(T, null)).hasSize(3);
        // no match → empty (honest failure surface for ON_CALL routing)
        assertThat(service().onCallRoster(T, "ONCOLOGY")).isEmpty();
    }
}
