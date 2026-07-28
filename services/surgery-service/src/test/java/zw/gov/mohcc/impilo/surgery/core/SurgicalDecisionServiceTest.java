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
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalDecisionEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalDecisionRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * S3 — the surgical decision-making record. Proves: reasoning fields apply and refine in place,
 * an invented final_decision value is refused, and the decided_by/decided_at pair is always
 * stamped together with the decision itself — the application-layer mirror of V004's own
 * three-way CHECK.
 */
@ExtendWith(MockitoExtension.class)
class SurgicalDecisionServiceTest {

    @Mock SurgicalDecisionRepository repository;
    @Mock SurgicalEpisodeRepository episodeRepository;

    private SurgicalDecisionService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SurgicalDecisionService(repository, episodeRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.findByIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.of(new SurgicalEpisodeEntity()));
        lenient().when(repository.save(any())).thenAnswer(i -> {
            SurgicalDecisionEntity e = i.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void recordingReasoningFieldsSucceedsWithNoFinalDecisionYet() {
        Map<String, Object> result = service.recordDecision(episodeId, Map.of(
                "naturalHistory", "Untreated, risk of perforation and peritonitis",
                "expectedBenefit", "Resolution of recurrent biliary colic",
                "materialRisksConsidered", "Bleeding, bile duct injury, conversion to open"));

        assertEquals("Untreated, risk of perforation and peritonitis", result.get("naturalHistory"));
        assertNull(result.get("finalDecision"), "no decision has been made yet");
        assertNull(result.get("decidedBy"));
        assertNull(result.get("decidedAt"));
    }

    @Test
    void recordingForAnUnknownEpisodeIsNotFound() {
        when(episodeRepository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordDecision(episodeId, Map.of("naturalHistory", "x")));
        assertEquals("SURGICAL_EPISODE_NOT_FOUND", ex.getCode());
    }

    @Test
    void anInventedFinalDecisionIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recordDecision(episodeId, Map.of("finalDecision", "MAYBE_LATER")));
        assertEquals("INVALID_FINAL_DECISION", ex.getCode());
    }

    /** The three-way pairing: setting finalDecision always stamps decidedBy/decidedAt together. */
    @Test
    void aValidFinalDecisionStampsDecidedByAndDecidedAtTogether() {
        Map<String, Object> result = service.recordDecision(episodeId, Map.of("finalDecision", "PROCEED"));

        assertEquals("PROCEED", result.get("finalDecision"));
        assertEquals("actor-surgeon", result.get("decidedBy"));
        assertNotNull(result.get("decidedAt"));
    }

    @Test
    void anExplicitDecidedByOverridesTheActingClinicianWhenSupplied() {
        Map<String, Object> result = service.recordDecision(episodeId, Map.of(
                "finalDecision", "DO_NOT_PROCEED", "decidedBy", "consultant-lead"));

        assertEquals("consultant-lead", result.get("decidedBy"));
    }

    /** Refinement, not versioning: a second call on the same episode updates the one row. */
    @Test
    void decidingTwiceOnTheSameEpisodeRefinesTheSameRow() {
        SurgicalDecisionEntity existing = new SurgicalDecisionEntity();
        existing.setId(UUID.randomUUID());
        existing.setSurgicalEpisodeId(episodeId);
        existing.setTenantId(tenant);
        existing.setNaturalHistory("Initial reasoning");
        when(repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.of(existing));

        Map<String, Object> result = service.recordDecision(episodeId, Map.of("finalDecision", "DEFER"));

        assertEquals("Initial reasoning", result.get("naturalHistory"), "prior reasoning is preserved");
        assertEquals("DEFER", result.get("finalDecision"));
    }

    @Test
    void stomaAndImplantPossibilityFlagsApplyIndependently() {
        Map<String, Object> result = service.recordDecision(episodeId, Map.of(
                "stomaPossibility", true, "stomaPossibilityNotes", "Temporary loop ileostomy possible",
                "implantPossibility", false));

        assertEquals(true, result.get("stomaPossibility"));
        assertEquals("Temporary loop ileostomy possible", result.get("stomaPossibilityNotes"));
        assertEquals(false, result.get("implantPossibility"));
    }

    @Test
    void getDecisionForAnUndecidedEpisodeIsNotFound() {
        when(repository.findBySurgicalEpisodeIdAndTenantId(episodeId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.getDecision(episodeId));
        assertEquals("SURGICAL_DECISION_NOT_FOUND", ex.getCode());
    }
}
