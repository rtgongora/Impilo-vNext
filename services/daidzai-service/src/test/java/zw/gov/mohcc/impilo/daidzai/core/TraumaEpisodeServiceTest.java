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
}
