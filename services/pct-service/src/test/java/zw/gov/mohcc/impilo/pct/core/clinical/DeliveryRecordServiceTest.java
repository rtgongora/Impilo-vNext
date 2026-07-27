package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeliveryRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.DeliveryRecordRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Delivery-record service behaviour. The clinical CHECK constraints (caesarean references theatre,
 * severe tear records its repair, PPH carries its blood loss) are proven against real Postgres in
 * the migration probe; these tests cover the store-and-forward and single-delivery behaviour that a
 * constraint expresses only as a raw failure.
 */
class DeliveryRecordServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID EPISODE = UUID.randomUUID();

    private DeliveryRecordRepository repo;
    private DeliveryRecordService service;

    @BeforeEach
    void setUp() {
        repo = mock(DeliveryRecordRepository.class);
        service = new DeliveryRecordService(repo);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findForPregnancy(any(), any())).thenReturn(Optional.empty());
        when(repo.findByTenantIdAndClientOfflineId(any(), any())).thenReturn(Optional.empty());
    }

    private static DeliveryRecordEntity vaginalBirth() {
        DeliveryRecordEntity d = new DeliveryRecordEntity();
        d.setTenantId(TENANT);
        d.setMotherCpid("CPID-MOTHER");
        d.setPregnancyEpisodeId(EPISODE);
        d.setDeliveredAt(OffsetDateTime.now());
        d.setLabourOnset("SPONTANEOUS");
        d.setDeliveryMode(DeliveryRecordEntity.MODE_SPONTANEOUS_VAGINAL);
        d.setRecordedBy("midwife");
        return d;
    }

    @Test
    @DisplayName("a birth is recorded with defaults applied")
    void recordsABirth() {
        var saved = service.record(vaginalBirth());
        assertThat(saved.getDeliveryRecordId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(DeliveryRecordEntity.STATUS_RECORDED);
        assertThat(saved.getPphSuspected()).isFalse();
        assertThat(saved.getRecordedAt()).isNotNull();
    }

    @Test
    @DisplayName("a replayed offline packet returns the existing record, not a second birth")
    void offlineReplayIsIdempotent() {
        var existing = vaginalBirth();
        existing.setDeliveryRecordId(UUID.randomUUID());
        existing.setClientOfflineId("device-9");
        when(repo.findByTenantIdAndClientOfflineId(TENANT, "device-9"))
                .thenReturn(Optional.of(existing));

        var incoming = vaginalBirth();
        incoming.setClientOfflineId("device-9");

        assertThat(service.record(incoming).getDeliveryRecordId())
                .isEqualTo(existing.getDeliveryRecordId());
    }

    @Test
    @DisplayName("a second delivery for one pregnancy is a reconcilable conflict, not a 500")
    void secondDeliveryForOnePregnancyConflicts() {
        var existing = vaginalBirth();
        existing.setDeliveryRecordId(UUID.randomUUID());
        when(repo.findForPregnancy(TENANT, EPISODE)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.record(vaginalBirth()))
                .isInstanceOf(DeliveryRecordService.DuplicateDeliveryException.class)
                .hasMessageContaining("already has a delivery")
                .extracting(e -> ((DeliveryRecordService.DuplicateDeliveryException) e)
                        .getExistingDeliveryRecordId())
                .isEqualTo(existing.getDeliveryRecordId());
    }

    @Test
    @DisplayName("the conflict message points twins at the babiesDelivered count, not a second row")
    void twinsAreOneDeliveryNotTwo() {
        var existing = vaginalBirth();
        existing.setDeliveryRecordId(UUID.randomUUID());
        when(repo.findForPregnancy(TENANT, EPISODE)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.record(vaginalBirth()))
                .hasMessageContaining("babiesDelivered");
    }

    @Test
    @DisplayName("a delivery with no pregnancy episode still records (it just isn't episode-deduplicated)")
    void deliveryWithoutEpisodeStillRecords() {
        var d = vaginalBirth();
        d.setPregnancyEpisodeId(null);
        var saved = service.record(d);
        assertThat(saved.getDeliveryRecordId()).isNotNull();
    }
}
