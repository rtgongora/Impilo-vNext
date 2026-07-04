package zw.gov.mohcc.impilo.ubomi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.BirthNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.BirthNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CRVS birth notification lifecycle: SUBMITTED → REGISTERED with outbox provenance. */
@ExtendWith(MockitoExtension.class)
class BirthNotificationServiceTest {

    @Mock BirthNotificationRepository repo;
    @Mock EventOutboxRepository outbox;
    BirthNotificationService service;
    final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() { service = new BirthNotificationService(repo, outbox); }

    private BirthNotificationEntity submitted() {
        BirthNotificationEntity e = new BirthNotificationEntity();
        e.setId(7L);
        e.setTenantId(TENANT);
        e.setNotificationNumber("BN-2026-000007");
        e.setMotherCpid("CPID-M-1");
        e.setStatus("SUBMITTED");
        return e;
    }

    @Test
    void submit_sets_status_and_emits_birth_submitted() {
        BirthNotificationEntity e = submitted();
        e.setStatus(null);
        when(repo.save(any())).thenReturn(e);

        BirthNotificationEntity saved = service.submit(e);

        assertThat(saved.getStatus()).isEqualTo("SUBMITTED");
        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outbox).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("BIRTH_SUBMITTED");
        assertThat(captor.getValue().getPayload()).contains("BN-2026-000007");
    }

    @Test
    void approve_transitions_to_registered_and_emits_for_vito() {
        BirthNotificationEntity e = submitted();
        when(repo.findByTenantIdAndId(TENANT, 7L)).thenReturn(Optional.of(e));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BirthNotificationEntity registered = service.approve(TENANT, 7L);

        assertThat(registered.getStatus()).isEqualTo("REGISTERED");
        assertThat(registered.getRegisteredAt()).isNotNull();
        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outbox).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("BIRTH_REGISTERED");
        assertThat(captor.getValue().getPayload()).contains("CPID-M-1");
    }

    @Test
    void approve_accepts_validated_state() {
        BirthNotificationEntity e = submitted();
        e.setStatus("VALIDATED");
        when(repo.findByTenantIdAndId(TENANT, 7L)).thenReturn(Optional.of(e));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.approve(TENANT, 7L).getStatus()).isEqualTo("REGISTERED");
    }

    @Test
    void approve_rejects_already_registered() {
        BirthNotificationEntity e = submitted();
        e.setStatus("REGISTERED");
        when(repo.findByTenantIdAndId(TENANT, 7L)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.approve(TENANT, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REGISTERED");
    }

    @Test
    void approve_unknown_notification_throws() {
        when(repo.findByTenantIdAndId(TENANT, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(TENANT, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
