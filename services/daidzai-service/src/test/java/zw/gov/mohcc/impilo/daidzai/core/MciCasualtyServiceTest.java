package zw.gov.mohcc.impilo.daidzai.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import zw.gov.mohcc.impilo.daidzai.events.DaidzaiEventEmitter;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.MciCasualtyEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EmergencyIncidentRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.MciCasualtyRepository;

/**
 * MCI casualty tracking (V202, W11). Proves idempotent retagging (the scene tag number IS the
 * idempotency key), the surge threshold declaring once and never clearing, and the bulk-mint
 * correlation contract (unminted listing + link-episode idempotency/conflict).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MciCasualtyServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID INCIDENT = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Mock MciCasualtyRepository casualtyRepo;
    @Mock EmergencyIncidentRepository incidentRepo;
    @Mock DaidzaiEventEmitter emitter;
    private MciCasualtyService service;
    private final List<MciCasualtyEntity> store = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new MciCasualtyService(casualtyRepo, incidentRepo, emitter);

        EmergencyIncidentEntity incident = new EmergencyIncidentEntity();
        incident.setId(INCIDENT);
        incident.setTenantId(TENANT);
        incident.setIncidentType("MCI");
        when(incidentRepo.findByIdAndTenantId(INCIDENT, TENANT)).thenReturn(Optional.of(incident));

        store.clear();
        when(casualtyRepo.findByTenantIdAndIncidentIdOrderByTaggedAtAsc(TENANT, INCIDENT))
                .thenAnswer(inv -> new ArrayList<>(store));
        when(casualtyRepo.countByTenantIdAndIncidentId(TENANT, INCIDENT))
                .thenAnswer(inv -> (long) store.size());
        when(casualtyRepo.save(any())).thenAnswer(inv -> {
            MciCasualtyEntity c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
                store.add(c);
            }
            return c;
        });
    }

    @Test
    @DisplayName("tagging an unknown incident is refused")
    void unknownIncidentRefused() {
        when(incidentRepo.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.tagCasualty(TENANT, UUID.randomUUID(), "T-001", "IMMEDIATE", null, "medic-A"))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    @DisplayName("an invalid sieve category is refused")
    void invalidSieveCategoryRefused() {
        assertThatThrownBy(() -> service.tagCasualty(TENANT, INCIDENT, "T-001", "BOGUS", null, "medic-A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a correct tag succeeds")
    void tagSucceeds() {
        var c = service.tagCasualty(TENANT, INCIDENT, "T-001", "immediate", null, "medic-A");
        assertThat(c.getSieveCategory()).isEqualTo("IMMEDIATE");
        assertThat(c.getStatus()).isEqualTo("ON_SCENE");
    }

    @Test
    @DisplayName("retagging the SAME casualty_reference updates the same record, not a duplicate")
    void retaggingIsIdempotent() {
        var first = service.tagCasualty(TENANT, INCIDENT, "T-001", "DELAYED", null, "medic-A");
        var second = service.tagCasualty(TENANT, INCIDENT, "T-001", "IMMEDIATE", null, "medic-B");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getSieveCategory()).isEqualTo("IMMEDIATE");
        assertThat(store).hasSize(1);
    }

    @Test
    @DisplayName("surge is NOT declared below the threshold")
    void noSurgeBelowThreshold() {
        for (int i = 0; i < MciCasualtyService.SURGE_THRESHOLD - 1; i++) {
            service.tagCasualty(TENANT, INCIDENT, "T-" + i, "DELAYED", null, "medic-A");
        }
        // incidentRepo.save is only called by checkSurge — never triggered below threshold.
        org.mockito.Mockito.verify(incidentRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("surge IS declared the moment the threshold is crossed, and stamped only ONCE")
    void surgeDeclaredExactlyOnceAtThreshold() {
        EmergencyIncidentEntity[] savedIncident = new EmergencyIncidentEntity[1];
        org.mockito.Mockito.doAnswer(inv -> {
            savedIncident[0] = inv.getArgument(0);
            return savedIncident[0];
        }).when(incidentRepo).save(any());

        for (int i = 0; i < MciCasualtyService.SURGE_THRESHOLD; i++) {
            service.tagCasualty(TENANT, INCIDENT, "T-" + i, "DELAYED", null, "medic-A");
        }

        org.mockito.Mockito.verify(incidentRepo, org.mockito.Mockito.times(1)).save(any());
        assertThat(savedIncident[0].getSurgeDeclaredAt()).isNotNull();
        assertThat(savedIncident[0].getSurgeCasualtyCount()).isEqualTo(MciCasualtyService.SURGE_THRESHOLD);
    }

    @Test
    @DisplayName("listUnminted delegates to the repository's own unminted query")
    void listUnmintedDelegates() {
        when(casualtyRepo.findUnminted(TENANT, INCIDENT)).thenReturn(List.of(new MciCasualtyEntity()));
        assertThat(service.listUnminted(TENANT, INCIDENT)).hasSize(1);
    }

    @Test
    @DisplayName("linkEpisode sets the correlation id on first call")
    void linkEpisodeSetsCorrelation() {
        MciCasualtyEntity c = new MciCasualtyEntity();
        UUID casualtyId = UUID.randomUUID();
        c.setId(casualtyId);
        c.setTenantId(TENANT);
        when(casualtyRepo.findByTenantIdAndId(TENANT, casualtyId)).thenReturn(Optional.of(c));
        org.mockito.Mockito.doAnswer(inv -> inv.getArgument(0)).when(casualtyRepo).save(any());

        UUID episodeId = UUID.randomUUID();
        var linked = service.linkEpisode(TENANT, casualtyId, episodeId);
        assertThat(linked.getEmergencyEpisodeId()).isEqualTo(episodeId);
    }

    @Test
    @DisplayName("linkEpisode refuses to overwrite an existing DIFFERENT episode correlation")
    void linkEpisodeRefusesConflictingOverwrite() {
        MciCasualtyEntity c = new MciCasualtyEntity();
        UUID casualtyId = UUID.randomUUID();
        c.setId(casualtyId);
        c.setTenantId(TENANT);
        c.setEmergencyEpisodeId(UUID.randomUUID());
        when(casualtyRepo.findByTenantIdAndId(TENANT, casualtyId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.linkEpisode(TENANT, casualtyId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }
}
