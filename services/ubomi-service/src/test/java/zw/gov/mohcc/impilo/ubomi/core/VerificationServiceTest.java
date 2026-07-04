package zw.gov.mohcc.impilo.ubomi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.BirthNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.DeathNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.VerificationLogEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.BirthNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.DeathNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.VerificationLogRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Vital-event verification: every lookup (hit or miss) leaves an audit log row. */
@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock BirthNotificationRepository births;
    @Mock DeathNotificationRepository deaths;
    @Mock VerificationLogRepository logs;
    VerificationService service;
    final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() { service = new VerificationService(births, deaths, logs); }

    @Test
    void birth_registration_verifies_and_logs() {
        BirthNotificationEntity b = new BirthNotificationEntity();
        b.setNotificationNumber("BN-1");
        b.setCivilRegNumber("CRN-1");
        b.setStatus("REGISTERED");
        when(births.findByTenantIdAndNotificationNumber(TENANT, "BN-1")).thenReturn(Optional.of(b));

        VerificationService.VerificationResult r = service.verify(TENANT, "BN-1", "actor-1", "INTERNAL");

        assertThat(r.eventType()).isEqualTo("BIRTH");
        assertThat(r.verificationResult()).isEqualTo("VERIFIED");
        assertThat(r.civilRegNumber()).isEqualTo("CRN-1");
        ArgumentCaptor<VerificationLogEntity> captor = ArgumentCaptor.forClass(VerificationLogEntity.class);
        verify(logs).save(captor.capture());
        assertThat(captor.getValue().getVerificationResult()).isEqualTo("VERIFIED");
        assertThat(captor.getValue().getRequestedByActor()).isEqualTo("actor-1");
    }

    @Test
    void death_registration_found_after_birth_miss() {
        when(births.findByTenantIdAndNotificationNumber(TENANT, "DN-1")).thenReturn(Optional.empty());
        DeathNotificationEntity d = new DeathNotificationEntity();
        d.setNotificationNumber("DN-1");
        d.setStatus("CERTIFIED");
        when(deaths.findByTenantIdAndNotificationNumber(TENANT, "DN-1")).thenReturn(Optional.of(d));

        VerificationService.VerificationResult r = service.verify(TENANT, "DN-1", "actor-1", "INTERNAL");

        assertThat(r.eventType()).isEqualTo("DEATH");
        assertThat(r.notificationStatus()).isEqualTo("CERTIFIED");
    }

    @Test
    void unknown_registration_returns_not_found_and_still_logs() {
        when(births.findByTenantIdAndNotificationNumber(TENANT, "X")).thenReturn(Optional.empty());
        when(deaths.findByTenantIdAndNotificationNumber(TENANT, "X")).thenReturn(Optional.empty());

        VerificationService.VerificationResult r = service.verify(TENANT, "X", "actor-2", "EXTERNAL");

        assertThat(r.verificationResult()).isEqualTo("NOT_FOUND");
        assertThat(r.eventType()).isNull();
        ArgumentCaptor<VerificationLogEntity> captor = ArgumentCaptor.forClass(VerificationLogEntity.class);
        verify(logs).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("UNKNOWN");
        assertThat(captor.getValue().getVerificationResult()).isEqualTo("NOT_FOUND");
    }
}
