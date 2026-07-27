package zw.gov.mohcc.impilo.daidzai.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyRequestEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.TraumaEpisodeEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.TraumaEpisodePhaseEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.EventOutboxRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Domain coverage for the canonical trauma-episode spine: dual-entry mint idempotency, the
 * read-model phase timeline, and the incident→episode wiring (DAIDZAI mints on triage).
 */
@SpringBootTest
@ActiveProfiles("test")
class TraumaEpisodeServiceTest {

    @Autowired private TraumaEpisodeService episodes;
    @Autowired private EmergencyService emergency;
    @Autowired private EventOutboxRepository outboxRepo;

    @Test
    void mintIsIdempotentOnTenantAndOriginKey() {
        UUID tenant = UUID.randomUUID();
        String originKey = "incident-" + UUID.randomUUID();

        TraumaEpisodeEntity first = episodes.mint(tenant, TraumaEpisodeService.OWNER_DAIDZAI, "INCIDENT",
                originKey, UUID.randomUUID(), "UNKNOWN", null, "TMP-1", "INCIDENT", originKey);
        TraumaEpisodeEntity again = episodes.mint(tenant, TraumaEpisodeService.OWNER_DAIDZAI, "INCIDENT",
                originKey, UUID.randomUUID(), "UNKNOWN", null, "TMP-1", "INCIDENT", originKey);

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(first.getStatus()).isEqualTo("OPEN");
        assertThat(first.getEpisodeReference()).startsWith("TEP-");
        // The retried mint must not fork the spine nor double the seed phase row.
        List<TraumaEpisodePhaseEntity> timeline = episodes.timeline(tenant, first.getId());
        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).getPhase()).isEqualTo("INCIDENT");
        assertThat(outboxRepo.findAll())
                .filteredOn(e -> e.getEventType().equals("daidzai.trauma_episode.minted")
                        && e.getAggregateId().equals(first.getId().toString()))
                .hasSize(1);
    }

    @Test
    void differentTenantSameOriginKeyMintsDistinctEpisodes() {
        String originKey = "shared-key-" + UUID.randomUUID();
        TraumaEpisodeEntity a = episodes.mint(UUID.randomUUID(), TraumaEpisodeService.OWNER_DAIDZAI,
                "INCIDENT", originKey, null, "UNKNOWN", null, null, "INCIDENT", originKey);
        TraumaEpisodeEntity b = episodes.mint(UUID.randomUUID(), TraumaEpisodeService.OWNER_DAIDZAI,
                "INCIDENT", originKey, null, "UNKNOWN", null, null, "INCIDENT", originKey);
        assertThat(a.getId()).isNotEqualTo(b.getId());
    }

    @Test
    void phaseRegistrationBuildsAnOrderedResolvableTimelineAndIsIdempotent() {
        UUID tenant = UUID.randomUUID();
        String originKey = "ed-" + UUID.randomUUID();
        TraumaEpisodeEntity ep = episodes.mint(tenant, "pct-service", "ED_WALK_IN",
                originKey, null, "PROVISIONAL", "HID-PROV-1", null, "ED", originKey);

        episodes.registerPhase(tenant, ep.getId(), "RESUS", "inpatient-service",
                "resus-1", "IN_PROGRESS", "inpatient.resuscitation.recorded", null);
        episodes.registerPhase(tenant, ep.getId(), "BLOOD", "madi-service",
                "order-1", "RESERVED", "madi.blood.ordered", null);
        // Redelivered/retried registration must not double the timeline.
        episodes.registerPhase(tenant, ep.getId(), "BLOOD", "madi-service",
                "order-1", "RESERVED", "madi.blood.ordered", null);

        Map<String, Object> view = episodes.episodeView(tenant, ep.getId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) view.get("timeline");
        assertThat(timeline).extracting(m -> m.get("phase"))
                .containsExactly("ED", "RESUS", "BLOOD"); // ED seed + 2 distinct phases, no dup
        assertThat(view.get("currentPhase")).isEqualTo("BLOOD");
        assertThat(view.get("originService")).isEqualTo("pct-service");
    }

    @Test
    void incidentTriageMintsAndStampsTheEpisodeOnTheIncident() {
        UUID tenant = UUID.randomUUID();
        EmergencyRequestEntity r = emergency.createRequest(tenant, "BYSTANDER", null,
                "ANONYMOUS", null, null, "adult male RTC", "TRAUMA", "CRITICAL",
                "RTC on highway", -17.8, 31.0, "highway", null, "MOBILE");
        EmergencyIncidentEntity inc = emergency.triageRequestToIncident(tenant, r.getId(), "triager-1");

        assertThat(inc.getTraumaEpisodeId()).isNotNull();
        TraumaEpisodeEntity ep = episodes.getEpisode(tenant, inc.getTraumaEpisodeId());
        assertThat(ep.getIncidentId()).isEqualTo(inc.getId());
        assertThat(ep.getOriginKind()).isEqualTo("INCIDENT");
        assertThat(ep.getOriginKey()).isEqualTo(inc.getId().toString());
        // Idempotent: re-triage-like re-mint on the same incident id returns the same episode.
        TraumaEpisodeEntity re = episodes.mint(tenant, TraumaEpisodeService.OWNER_DAIDZAI, "INCIDENT",
                inc.getId().toString(), inc.getId(), "ANONYMOUS", null, null, "INCIDENT", inc.getId().toString());
        assertThat(re.getId()).isEqualTo(ep.getId());
    }

    // ── V-3 closure: continuum-link on facility arrival ──────────────────────────────────────

    @Test
    void continuumLinkAnchorsTheEpisodeAndEmitsOnce() {
        UUID tenant = UUID.randomUUID();
        TraumaEpisodeEntity ep = episodes.mint(tenant, TraumaEpisodeService.OWNER_DAIDZAI, "INCIDENT",
                "inc-" + UUID.randomUUID(), UUID.randomUUID(), "UNKNOWN", null, "TMP-9", "INCIDENT", null);
        UUID emEp = UUID.randomUUID();

        TraumaEpisodeEntity linked = episodes.continuumLink(tenant, ep.getId(), "J-0001", emEp);

        assertThat(linked.getPctJourneyId()).isEqualTo("J-0001");
        assertThat(linked.getPctEmergencyEpisodeId()).isEqualTo(emEp);
        assertThat(linked.getContinuumLinkedAt()).isNotNull();
        assertThat(outboxRepo.findAll())
                .filteredOn(e -> e.getEventType().equals("daidzai.trauma_episode.continuum_linked")
                        && e.getAggregateId().equals(ep.getId().toString()))
                .hasSize(1);
    }

    @Test
    void continuumLinkToTheSameJourneyIsAnIdempotentNoOp() {
        UUID tenant = UUID.randomUUID();
        TraumaEpisodeEntity ep = episodes.mint(tenant, TraumaEpisodeService.OWNER_DAIDZAI, "INCIDENT",
                "inc-" + UUID.randomUUID(), null, "UNKNOWN", null, null, "INCIDENT", null);
        episodes.continuumLink(tenant, ep.getId(), "J-SAME", null);
        episodes.continuumLink(tenant, ep.getId(), "J-SAME", null);

        // No second event — a replay is not a new anchoring.
        assertThat(outboxRepo.findAll())
                .filteredOn(e -> e.getEventType().equals("daidzai.trauma_episode.continuum_linked")
                        && e.getAggregateId().equals(ep.getId().toString()))
                .hasSize(1);
    }

    @Test
    void continuumLinkToADifferentJourneyIsRefused() {
        UUID tenant = UUID.randomUUID();
        TraumaEpisodeEntity ep = episodes.mint(tenant, TraumaEpisodeService.OWNER_DAIDZAI, "INCIDENT",
                "inc-" + UUID.randomUUID(), null, "UNKNOWN", null, null, "INCIDENT", null);
        episodes.continuumLink(tenant, ep.getId(), "J-FIRST", null);

        // One facility visit is one journey — a second, different journey is a correlation error.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> episodes.continuumLink(tenant, ep.getId(), "J-SECOND", null));
    }

    @Test
    void unanchoredSweepListsFacilityPhaseEpisodesWithoutAJourneyAndClearsOnLink() {
        UUID tenant = UUID.randomUUID();
        // A facility-phase episode with no anchor — exactly what the sweep must surface.
        TraumaEpisodeEntity ep = episodes.mint(tenant, "pct-service", "ED_WALK_IN",
                "ed-" + UUID.randomUUID(), null, "UNKNOWN", null, null, "ED", null);

        assertThat(episodes.unanchoredInFacility(tenant))
                .anyMatch(e -> e.getId().equals(ep.getId()));

        episodes.continuumLink(tenant, ep.getId(), "J-LINK", null);

        assertThat(episodes.unanchoredInFacility(tenant))
                .noneMatch(e -> e.getId().equals(ep.getId()));
    }

    @Test
    void prehospitalPhaseEpisodesAreNotFlaggedUnanchored() {
        UUID tenant = UUID.randomUUID();
        // Still prehospital — the window the spine exists to cover, so no anchor is expected yet.
        TraumaEpisodeEntity ep = episodes.mint(tenant, TraumaEpisodeService.OWNER_DAIDZAI, "INCIDENT",
                "inc-" + UUID.randomUUID(), null, "UNKNOWN", null, null, "DISPATCH", null);
        assertThat(episodes.unanchoredInFacility(tenant))
                .noneMatch(e -> e.getId().equals(ep.getId()));
    }

    // ── adopt: the two-entry-path duplicate ──────────────────────────────────────────────────

    @Test
    void adoptMarksTheAbsorbedEpisodeMergedAndCarriesTheAnchorAcross() {
        UUID tenant = UUID.randomUUID();
        // The walk-in (survivor, no anchor yet) and the SOS-line incident (absorbed, already linked).
        TraumaEpisodeEntity survivor = episodes.mint(tenant, "pct-service", "ED_WALK_IN",
                "ed-" + UUID.randomUUID(), null, "UNKNOWN", null, null, "ED", null);
        TraumaEpisodeEntity absorbed = episodes.mint(tenant, TraumaEpisodeService.OWNER_DAIDZAI, "INCIDENT",
                "inc-" + UUID.randomUUID(), null, "UNKNOWN", null, null, "PREHOSPITAL", null);
        episodes.continuumLink(tenant, absorbed.getId(), "J-CARRY", null);

        episodes.adopt(tenant, survivor.getId(), absorbed.getId(), "same patient: SOS call + walk-in");

        TraumaEpisodeEntity absorbedAfter = episodes.getEpisode(tenant, absorbed.getId());
        assertThat(absorbedAfter.getStatus()).isEqualTo("MERGED");
        assertThat(absorbedAfter.getMergedIntoId()).isEqualTo(survivor.getId());
        // The absorbed episode's anchor carried to the survivor, so the surviving spine resolves.
        TraumaEpisodeEntity survivorAfter = episodes.getEpisode(tenant, survivor.getId());
        assertThat(survivorAfter.getPctJourneyId()).isEqualTo("J-CARRY");
    }

    @Test
    void adoptIsIdempotentOnTheSamePairAndRejectsASelfMerge() {
        UUID tenant = UUID.randomUUID();
        TraumaEpisodeEntity survivor = episodes.mint(tenant, "pct-service", "ED_WALK_IN",
                "ed-" + UUID.randomUUID(), null, "UNKNOWN", null, null, "ED", null);
        TraumaEpisodeEntity absorbed = episodes.mint(tenant, TraumaEpisodeService.OWNER_DAIDZAI, "INCIDENT",
                "inc-" + UUID.randomUUID(), null, "UNKNOWN", null, null, "INCIDENT", null);

        episodes.adopt(tenant, survivor.getId(), absorbed.getId(), "dup");
        episodes.adopt(tenant, survivor.getId(), absorbed.getId(), "dup");   // replay: no throw
        assertThat(episodes.getEpisode(tenant, absorbed.getId()).getMergedIntoId()).isEqualTo(survivor.getId());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> episodes.adopt(tenant, survivor.getId(), survivor.getId(), "self"));
    }
}
