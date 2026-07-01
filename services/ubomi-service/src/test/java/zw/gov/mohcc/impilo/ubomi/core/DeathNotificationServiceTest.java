package zw.gov.mohcc.impilo.ubomi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.DeathNotificationEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.DeathNotificationRepository;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** WS#8 CRVS package validation + Registrar General handoff (no forged registration). */
@ExtendWith(MockitoExtension.class)
class DeathNotificationServiceTest {

    @Mock DeathNotificationRepository repo;
    @Mock EventOutboxRepository outbox;
    DeathNotificationService service;
    final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() { service = new DeathNotificationService(repo, outbox); }

    private DeathNotificationEntity complete() {
        DeathNotificationEntity e = new DeathNotificationEntity();
        e.setId(1L);
        e.setTenantId(TENANT);
        e.setDeceasedCpid("CPID-1");
        e.setDateOfDeath(LocalDate.now());
        e.setPlaceOfDeathType("FACILITY");
        e.setUnderlyingCause("Sepsis");
        e.setStatus("CERTIFIED");
        return e;
    }

    @Test
    void validate_flags_missing_fields() {
        DeathNotificationEntity e = new DeathNotificationEntity();
        e.setStatus("SUBMITTED");
        assertThat(service.validatePackage(e))
                .contains("deceasedIdentity", "dateOfDeath", "placeOfDeath", "underlyingCause", "certification");
    }

    @Test
    void package_ready_blocked_when_incomplete() {
        DeathNotificationEntity e = complete();
        e.setUnderlyingCause(null);
        when(repo.findByTenantIdAndId(TENANT, 1L)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.markPackageReady(TENANT, 1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("underlyingCause");
    }

    @Test
    void package_ready_when_complete() {
        DeathNotificationEntity e = complete();
        when(repo.findByTenantIdAndId(TENANT, 1L)).thenReturn(Optional.of(e));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outbox.save(any())).thenAnswer(i -> i.getArgument(0));
        DeathNotificationEntity r = service.markPackageReady(TENANT, 1L);
        assertThat(r.isPackageReady()).isTrue();
    }

    // A community/field death may complete on a verbal-autopsy PROBABLE cause (no medical certification),
    // and the probable basis is preserved — never counted as certified.
    @Test
    void package_ready_on_verbal_autopsy_probable() {
        DeathNotificationEntity e = new DeathNotificationEntity();
        e.setId(1L);
        e.setTenantId(TENANT);
        e.setDeceasedCpid("TMP-DEC-ABCD1234");
        e.setDateOfDeath(LocalDate.now());
        e.setPlaceOfDeathType("COMMUNITY");
        e.setUnderlyingCause("Malaria");
        e.setStatus("SUBMITTED"); // never medically certified
        e.setCauseOfDeathBasis("VERBAL_AUTOPSY_PROBABLE");
        assertThat(service.validatePackage(e)).isEmpty();
        when(repo.findByTenantIdAndId(TENANT, 1L)).thenReturn(Optional.of(e));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outbox.save(any())).thenAnswer(i -> i.getArgument(0));
        DeathNotificationEntity r = service.markPackageReady(TENANT, 1L);
        assertThat(r.isPackageReady()).isTrue();
        assertThat(r.getCauseOfDeathBasis()).isEqualTo("VERBAL_AUTOPSY_PROBABLE");
    }

    // A verbal-autopsy probable notification is NOT medically certified — the two are never conflated.
    @Test
    void probable_basis_is_not_certification() {
        DeathNotificationEntity e = new DeathNotificationEntity();
        e.setStatus("SUBMITTED");
        e.setCauseOfDeathBasis("VERBAL_AUTOPSY_PROBABLE");
        // certification requirement satisfied by the probable basis, but status is not CERTIFIED.
        assertThat(service.validatePackage(e)).doesNotContain("certification");
        assertThat(e.getStatus()).isNotEqualTo("CERTIFIED");
    }

    @Test
    void register_requires_civil_reg_number() {
        DeathNotificationEntity e = complete();
        when(repo.findByTenantIdAndId(TENANT, 1L)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.register(TENANT, 1L, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_only_from_certified() {
        DeathNotificationEntity e = complete();
        e.setStatus("SUBMITTED");
        when(repo.findByTenantIdAndId(TENANT, 1L)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.register(TENANT, 1L, "CR-100"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void register_sets_registered_with_real_number() {
        DeathNotificationEntity e = complete();
        when(repo.findByTenantIdAndId(TENANT, 1L)).thenReturn(Optional.of(e));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outbox.save(any())).thenAnswer(i -> i.getArgument(0));
        DeathNotificationEntity r = service.register(TENANT, 1L, "CR-2026-001");
        assertThat(r.getStatus()).isEqualTo("REGISTERED");
        assertThat(r.getCivilRegNumber()).isEqualTo("CR-2026-001");
        assertThat(r.getRegisteredAt()).isNotNull();
    }
}
