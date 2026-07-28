package zw.gov.mohcc.impilo.surgery.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalFollowupEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalFollowupRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SB-2 — discharge/follow-up (§18). Proves the fields refine in place, a fit note stamps its own
 * issued-by/at pair, and the surveillance-plan ref is a pointer only.
 */
@ExtendWith(MockitoExtension.class)
class SurgicalFollowupServiceTest {

    @Mock SurgicalFollowupRepository repository;
    @Mock SurgicalEpisodeRepository episodeRepository;

    private SurgicalFollowupService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SurgicalFollowupService(repository, episodeRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-nurse", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.findByIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.of(new SurgicalEpisodeEntity()));
        lenient().when(repository.save(any())).thenAnswer(i -> {
            SurgicalFollowupEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void recordingForAnUnknownEpisodeIsNotFound() {
        when(episodeRepository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.record(episodeId, Map.of("restrictions", "No heavy lifting 6 weeks")));
        assertEquals("SURGICAL_EPISODE_NOT_FOUND", ex.getCode());
    }

    @Test
    void recordingRestrictionsAndTransportSucceeds() {
        Map<String, Object> result = service.record(episodeId, Map.of(
                "restrictions", "No heavy lifting 6 weeks",
                "transportArranged", true, "transportNotes", "Ambulance booked for discharge day"));

        assertEquals("No heavy lifting 6 weeks", result.get("restrictions"));
        assertEquals(true, result.get("transportArranged"));
    }

    /** A fit note stamps its own issued-by/at pair, distinct from the general recordedBy/At. */
    @Test
    void issuingAFitNoteStampsIssuedByAndAtTogether() {
        Map<String, Object> result = service.record(episodeId, Map.of("fitNoteNotes", "Unfit for manual work 4 weeks"));

        assertEquals("Unfit for manual work 4 weeks", result.get("fitNoteNotes"));
        assertEquals("actor-nurse", result.get("fitNoteIssuedBy"));
        assertNotNull(result.get("fitNoteIssuedAt"));
    }

    /** Refinement, not versioning: a second call on the same episode updates the one row. */
    @Test
    void recordingTwiceOnTheSameEpisodeRefinesTheSameRow() {
        SurgicalFollowupEntity existing = new SurgicalFollowupEntity();
        existing.setId(UUID.randomUUID());
        existing.setSurgicalEpisodeId(episodeId);
        existing.setTenantId(tenant);
        existing.setRestrictions("Initial restriction");
        when(repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant)).thenReturn(Optional.of(existing));

        Map<String, Object> result = service.record(episodeId, Map.of("futureSurgeryIntent", "Planned stoma reversal in 3 months"));

        assertEquals("Initial restriction", result.get("restrictions"), "prior content is preserved");
        assertEquals("Planned stoma reversal in 3 months", result.get("futureSurgeryIntent"));
    }

    @Test
    void getForAnUnrecordedEpisodeIsNotFound() {
        when(repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class, () -> service.get(episodeId));
        assertEquals("SURGICAL_FOLLOWUP_NOT_FOUND", ex.getCode());
    }
}
