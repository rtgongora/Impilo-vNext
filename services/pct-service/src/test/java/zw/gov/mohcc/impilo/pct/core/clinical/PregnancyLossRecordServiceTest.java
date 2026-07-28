package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyLossRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.PregnancyLossRecordRepository;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pregnancy-loss service behaviour, and the structural no-person guarantee.
 *
 * <p>The doctrinal CHECKs (stillbirth needs its gestation, a certificate needs its notification
 * event, a termination needs its authorisation) are mutation-proven against real Postgres in the
 * migration probe. Here: offline idempotency, the FULL_CLINICAL default with the confidentiality
 * gap stated, and — by reflection — that the entity holds no baby-identity field, the same guarantee
 * the fetus record carries.
 */
class PregnancyLossRecordServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private PregnancyLossRecordRepository repo;
    private PregnancyLossRecordService service;

    @BeforeEach
    void setUp() {
        repo = mock(PregnancyLossRecordRepository.class);
        service = new PregnancyLossRecordService(repo);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());
    }

    private static PregnancyLossRecordEntity stillbirth() {
        PregnancyLossRecordEntity l = new PregnancyLossRecordEntity();
        l.setTenantId(TENANT);
        l.setMotherCpid("CPID-MOTHER");
        l.setPregnancyEpisodeId(UUID.randomUUID());
        l.setLossType(PregnancyLossRecordEntity.TYPE_STILLBIRTH_ANTEPARTUM);
        l.setOccurredOn(LocalDate.now());
        l.setRecordedBy("midwife");
        return l;
    }

    @Test
    @DisplayName("a loss is recorded with FULL_CLINICAL and the confidentiality gap stated")
    void recordsWithFullClinicalDefault() {
        var saved = service.record(stillbirth());
        assertThat(saved.getLossRecordId()).isNotNull();
        assertThat(saved.getConfidentialityCategory()).isEqualTo("FULL_CLINICAL");
        // The boolean gates default to false, so a null never reads as an affirmative.
        assertThat(saved.getCertificateIssued()).isFalse();
        assertThat(saved.getStillbirthCertifiable()).isFalse();
        assertThat(saved.getBereavementSupportOffered()).isFalse();
    }

    @Test
    @DisplayName("a replayed offline packet returns the existing record, not a second loss")
    void offlineReplayIsIdempotent() {
        var existing = stillbirth();
        existing.setLossRecordId(UUID.randomUUID());
        existing.setClientOfflineId("device-1");
        when(repo.findByTenantIdAndClientOfflineId(TENANT, "device-1")).thenReturn(Optional.of(existing));

        var incoming = stillbirth();
        incoming.setClientOfflineId("device-1");
        assertThat(service.record(incoming).getLossRecordId()).isEqualTo(existing.getLossRecordId());
    }

    @Test
    @DisplayName("the loss entity holds no baby-identity field — a stillbirth mints no person")
    void noBabyIdentityField() {
        // The PO ruling made structural: the only person on this record is the mother. Any field
        // that could hold a baby's identity would be the drift the ruling forbids.
        for (Field f : PregnancyLossRecordEntity.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            if (name.contains("cpid")) {
                assertThat(name)
                        .as("only mother_cpid may be an identity on a loss record; found %s", f.getName())
                        .isEqualTo("mothercpid");
            }
            assertThat(name)
                    .as("no baby/newborn/infant identity field may exist on a loss record")
                    .doesNotContain("baby", "newborn", "infant", "childcpid");
        }
    }
}
