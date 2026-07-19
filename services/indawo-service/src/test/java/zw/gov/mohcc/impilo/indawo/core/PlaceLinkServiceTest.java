package zw.gov.mohcc.impilo.indawo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.indawo.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.indawo.domain.PlaceLinkEntity;
import zw.gov.mohcc.impilo.indawo.domain.SiteEntity;
import zw.gov.mohcc.impilo.indawo.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.indawo.repository.PlaceLinkRepository;
import zw.gov.mohcc.impilo.indawo.repository.SiteRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-L2: typed cross-registry links — closed vocabulary, no self-links, one
 * ACTIVE link per (pair, type), Indawo-site refs must exist, Tuso refs opaque,
 * and every write publishes an outbox event.
 */
@ExtendWith(MockitoExtension.class)
class PlaceLinkServiceTest {

    @Mock private PlaceLinkRepository placeLinkRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private OutboxEventRepository outboxRepository;

    private PlaceLinkService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final UUID facilityUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PlaceLinkService(placeLinkRepository, siteRepository, outboxRepository, new ObjectMapper());
        lenient().when(placeLinkRepository.save(any())).thenAnswer(inv -> {
            PlaceLinkEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            if (e.getValidFrom() == null) {
                e.setValidFrom(java.time.OffsetDateTime.now());
            }
            return e;
        });
        lenient().when(siteRepository.findById(siteId))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(SiteEntity.class)));
    }

    private PlaceLinkService.CreateLink hostsServicePoint() {
        return new PlaceLinkService.CreateLink(
                PlaceLinkEntity.REF_INDAWO_SITE, siteId,
                PlaceLinkEntity.REF_TUSO_FACILITY, facilityUuid,
                "HOSTS_SERVICE_POINT", null, null, "steward-case:1");
    }

    @Test
    void createLinksSiteToFacilityAndPublishesEvent() {
        PlaceLinkEntity link = service.create(tenantId, "national-spine", UUID.randomUUID().toString(),
                "steward-1", hostsServicePoint());

        assertThat(link.getStatus()).isEqualTo(PlaceLinkEntity.STATUS_ACTIVE);
        assertThat(link.getStewardActor()).isEqualTo("steward-1");

        ArgumentCaptor<OutboxEventEntity> outbox = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("indawo.place_link.created");
        assertThat(outbox.getValue().getPayloadJson()).contains("HOSTS_SERVICE_POINT");
    }

    @Test
    void unknownLinkTypeAndSelfLinkAreRejected() {
        assertThatThrownBy(() -> service.create(tenantId, null, null, "steward-1",
                new PlaceLinkService.CreateLink(
                        PlaceLinkEntity.REF_INDAWO_SITE, siteId,
                        PlaceLinkEntity.REF_TUSO_FACILITY, facilityUuid,
                        "FRIENDS_WITH", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.create(tenantId, null, null, "steward-1",
                new PlaceLinkService.CreateLink(
                        PlaceLinkEntity.REF_INDAWO_SITE, siteId,
                        PlaceLinkEntity.REF_INDAWO_SITE, siteId,
                        "CONTAINS", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void unknownIndawoSiteRefIsRejectedButTusoRefsAreOpaque() {
        UUID unknownSite = UUID.randomUUID();
        when(siteRepository.findById(unknownSite)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(tenantId, null, null, "steward-1",
                new PlaceLinkService.CreateLink(
                        PlaceLinkEntity.REF_INDAWO_SITE, unknownSite,
                        PlaceLinkEntity.REF_TUSO_FACILITY, facilityUuid,
                        "LOCATED_WITHIN", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown Indawo site");

        // A TUSO_FACILITY ref is never existence-checked here — Tuso owns it.
        PlaceLinkEntity link = service.create(tenantId, null, null, "steward-1", hostsServicePoint());
        assertThat(link.getRightRefId()).isEqualTo(facilityUuid);
    }

    @Test
    void duplicateActiveLinkIsRejected() {
        when(placeLinkRepository
                .findFirstByTenantIdAndLeftRefTypeAndLeftRefIdAndRightRefTypeAndRightRefIdAndLinkTypeAndStatus(
                        any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new PlaceLinkEntity()));

        assertThatThrownBy(() -> service.create(tenantId, null, null, "steward-1", hostsServicePoint()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void endClosesActiveLinkAndPublishes() {
        PlaceLinkEntity link = new PlaceLinkEntity();
        link.setTenantId(tenantId);
        link.setLeftRefType(PlaceLinkEntity.REF_INDAWO_SITE);
        link.setLeftRefId(siteId);
        link.setRightRefType(PlaceLinkEntity.REF_TUSO_FACILITY);
        link.setRightRefId(facilityUuid);
        link.setLinkType("SAME_CAMPUS_AS");
        link.setId(UUID.randomUUID());
        link.setValidFrom(java.time.OffsetDateTime.now());
        when(placeLinkRepository.findByIdAndTenantId(link.getId(), tenantId)).thenReturn(Optional.of(link));

        PlaceLinkEntity ended = service.end(tenantId, null, null, "steward-2", link.getId());

        assertThat(ended.getStatus()).isEqualTo(PlaceLinkEntity.STATUS_ENDED);
        assertThat(ended.getValidTo()).isNotNull();
        ArgumentCaptor<OutboxEventEntity> outbox = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("indawo.place_link.ended");
    }
}
