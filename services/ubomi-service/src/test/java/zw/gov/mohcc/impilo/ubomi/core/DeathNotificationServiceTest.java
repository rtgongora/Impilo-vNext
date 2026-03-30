package zw.gov.mohcc.impilo.ubomi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.ubomi.integration.VitoClient;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.DeathNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.DeathNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeathNotificationServiceTest {

    @Mock
    private DeathNotificationRepository deathRepository;

    @Mock
    private EventOutboxRepository outboxRepository;

    @Mock
    private VitoClient vitoClient;

    @Mock
    private KafkaTemplate<String, String> trustChannelKafkaTemplate;

    private DeathNotificationService service;

    @BeforeEach
    void setUp() {
        service = new DeathNotificationService(deathRepository, outboxRepository, vitoClient, trustChannelKafkaTemplate);
    }

    @Test
    void register_publishes_to_trust_revocation_identity_topic() {
        UUID tenantId = UUID.randomUUID();
        Long notificationId = 1L;

        DeathNotificationEntity entity = new DeathNotificationEntity();
        entity.setId(notificationId);
        entity.setTenantId(tenantId);
        entity.setDeceasedCpid("CPID-DECEASED-001");
        entity.setNotificationNumber("DN-2026-001");
        entity.setStatus("CERTIFIED");
        entity.setFacilityId(UUID.randomUUID());
        entity.setNotifiedByActor("actor-001");
        entity.setDateOfDeath(java.time.LocalDate.now());
        entity.setPlaceOfDeathType("HOSPITAL");

        when(deathRepository.findByTenantIdAndId(tenantId, notificationId)).thenReturn(Optional.of(entity));
        when(deathRepository.save(any())).thenReturn(entity);
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(tenantId, notificationId);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(trustChannelKafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("trust.revocation.identity");
        assertThat(keyCaptor.getValue()).isEqualTo("CPID-DECEASED-001");
        assertThat(payloadCaptor.getValue()).contains("CPID-DECEASED-001");
        assertThat(payloadCaptor.getValue()).contains("impilo.ubomi.identity.revoked.v1");
    }

    @Test
    void register_calls_vito_client_death_notif() {
        UUID tenantId = UUID.randomUUID();
        Long notificationId = 2L;

        DeathNotificationEntity entity = new DeathNotificationEntity();
        entity.setId(notificationId);
        entity.setTenantId(tenantId);
        entity.setDeceasedCpid("CPID-DECEASED-002");
        entity.setNotificationNumber("DN-2026-002");
        entity.setStatus("CERTIFIED");
        entity.setFacilityId(UUID.randomUUID());
        entity.setNotifiedByActor("actor-001");
        entity.setDateOfDeath(java.time.LocalDate.now());
        entity.setPlaceOfDeathType("HOME");

        when(deathRepository.findByTenantIdAndId(tenantId, notificationId)).thenReturn(Optional.of(entity));
        when(deathRepository.save(any())).thenReturn(entity);
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(tenantId, notificationId);

        verify(vitoClient).notifyDeath(
                eq("CPID-DECEASED-002"),
                eq("DN-2026-002"),
                eq(tenantId.toString())
        );
    }

    @Test
    void register_publishes_death_registered_outbox_event() {
        UUID tenantId = UUID.randomUUID();
        Long notificationId = 3L;

        DeathNotificationEntity entity = new DeathNotificationEntity();
        entity.setId(notificationId);
        entity.setTenantId(tenantId);
        entity.setDeceasedCpid("CPID-DECEASED-003");
        entity.setNotificationNumber("DN-2026-003");
        entity.setStatus("CERTIFIED");
        entity.setFacilityId(UUID.randomUUID());
        entity.setNotifiedByActor("actor-001");
        entity.setDateOfDeath(java.time.LocalDate.now());
        entity.setPlaceOfDeathType("HOSPITAL");

        when(deathRepository.findByTenantIdAndId(tenantId, notificationId)).thenReturn(Optional.of(entity));
        when(deathRepository.save(any())).thenReturn(entity);
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(tenantId, notificationId);

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(outboxCaptor.capture());

        boolean hasDeathRegisteredEvent = outboxCaptor.getAllValues().stream()
                .anyMatch(e -> "DEATH_REGISTERED".equals(e.getEventType()));

        assertThat(hasDeathRegisteredEvent).isTrue();
    }

    @Test
    void register_throws_when_not_in_certified_status() {
        UUID tenantId = UUID.randomUUID();
        Long notificationId = 4L;

        DeathNotificationEntity entity = new DeathNotificationEntity();
        entity.setId(notificationId);
        entity.setTenantId(tenantId);
        entity.setStatus("SUBMITTED");

        when(deathRepository.findByTenantIdAndId(tenantId, notificationId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.register(tenantId, notificationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot register death notification in status: SUBMITTED");
    }
}
