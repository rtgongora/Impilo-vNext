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
import zw.gov.mohcc.impilo.surgery.integration.PctProblemContributionClient;
import zw.gov.mohcc.impilo.surgery.integration.PctProblemContributionClient.ContributionResult;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalComplicationPathwayEntity;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalComplicationPathwayRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SB-1 — complication pathway instances (§15, R8). Proves the full lifecycle order (recognise →
 * grade → own → disclose → close) and that closure is refused at each missing precondition, plus
 * the best-effort contribution into pct_problems on close.
 */
@ExtendWith(MockitoExtension.class)
class ComplicationPathwayServiceTest {

    @Mock SurgicalComplicationPathwayRepository repository;
    @Mock SurgicalEpisodeRepository episodeRepository;
    @Mock PctProblemContributionClient pctClient;

    private ComplicationPathwayService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();
    private final UUID pathwayId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ComplicationPathwayService(repository, episodeRepository, pctClient);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        lenient().when(episodeRepository.findByIdAndTenantId(episodeId, tenant))
                .thenReturn(Optional.of(episode()));
        lenient().when(repository.save(any())).thenAnswer(i -> {
            SurgicalComplicationPathwayEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(pathwayId);
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private SurgicalEpisodeEntity episode() {
        SurgicalEpisodeEntity e = new SurgicalEpisodeEntity();
        e.setId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-1");
        e.setJourneyId("journey-1");
        return e;
    }

    private SurgicalComplicationPathwayEntity openPathway() {
        SurgicalComplicationPathwayEntity e = new SurgicalComplicationPathwayEntity();
        e.setId(pathwayId);
        e.setTenantId(tenant);
        e.setSurgicalEpisodeId(episodeId);
        e.setComplicationType("ANASTOMOTIC_LEAK");
        e.setRecognisedBy("dr-x");
        e.setRecognisedAt(java.time.OffsetDateTime.now());
        e.setStatus("OPEN");
        return e;
    }

    @Test
    void recognisingAnInventedComplicationTypeIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.recognise(episodeId, Map.of("complicationType", "ALIEN_ABDUCTION")));
        assertEquals("INVALID_COMPLICATION_TYPE", ex.getCode());
    }

    @Test
    void recognisingAValidComplicationTypeSucceeds() {
        Map<String, Object> result = service.recognise(episodeId, Map.of("complicationType", "ANASTOMOTIC_LEAK"));

        assertEquals("ANASTOMOTIC_LEAK", result.get("complicationType"));
        assertEquals("OPEN", result.get("status"));
        assertEquals("actor-surgeon", result.get("recognisedBy"));
    }

    /** Vocabulary validation happens before the pathway is even looked up. */
    @Test
    void gradingWithAnInventedCodeIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.grade(pathwayId, "GRADE_X"));
        assertEquals("INVALID_GRADE", ex.getCode());
    }

    @Test
    void closingWithNoGradeYetIsRefused() {
        when(repository.findByIdAndTenantId(pathwayId, tenant)).thenReturn(Optional.of(openPathway()));

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.close(pathwayId, Map.of("outcome", "Resolved with reoperation")));
        assertEquals("PATHWAY_NOT_GRADED", ex.getCode());
    }

    @Test
    void closingWithNoOwnerIsRefusedEvenIfGraded() {
        SurgicalComplicationPathwayEntity e = openPathway();
        e.setGradeCode("IIIB");
        when(repository.findByIdAndTenantId(pathwayId, tenant)).thenReturn(Optional.of(e));

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.close(pathwayId, Map.of("outcome", "Resolved with reoperation")));
        assertEquals("PATHWAY_NOT_OWNED", ex.getCode());
    }

    @Test
    void closingWithNoDisclosureIsRefusedEvenIfGradedAndOwned() {
        SurgicalComplicationPathwayEntity e = openPathway();
        e.setGradeCode("IIIB");
        e.setOwnedBy("General Surgery Team B");
        when(repository.findByIdAndTenantId(pathwayId, tenant)).thenReturn(Optional.of(e));

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.close(pathwayId, Map.of("outcome", "Resolved with reoperation")));
        assertEquals("PATHWAY_NOT_DISCLOSED", ex.getCode());
    }

    @Test
    void closingWithNoOutcomeIsRefused() {
        SurgicalComplicationPathwayEntity e = openPathway();
        e.setGradeCode("IIIB");
        e.setOwnedBy("General Surgery Team B");
        e.setDisclosedBy("dr-x");
        e.setDisclosedAt(java.time.OffsetDateTime.now());
        when(repository.findByIdAndTenantId(pathwayId, tenant)).thenReturn(Optional.of(e));

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.close(pathwayId, Map.of()));
        assertEquals("OUTCOME_REQUIRED", ex.getCode());
    }

    /** The full order, all preconditions satisfied — and the resulting contribution to pct_problems. */
    @Test
    void closingAFullyManagedPathwayContributesToPctProblems() {
        SurgicalComplicationPathwayEntity e = openPathway();
        e.setGradeCode("IIIB");
        e.setOwnedBy("General Surgery Team B");
        e.setDisclosedBy("dr-x");
        e.setDisclosedAt(java.time.OffsetDateTime.now());
        when(repository.findByIdAndTenantId(pathwayId, tenant)).thenReturn(Optional.of(e));
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(episode()));
        UUID problemId = UUID.randomUUID();
        when(pctClient.contributeCondition(any(), any(), any(), any(), any(), any()))
                .thenReturn(new ContributionResult(problemId, true));

        Map<String, Object> result = service.close(pathwayId, Map.of("outcome", "Resolved with reoperation"));

        assertEquals("CLOSED", result.get("status"));
        assertEquals("Resolved with reoperation", result.get("outcome"));
        assertEquals(problemId.toString(), result.get("pctProblemRef"));
    }

    /** Best-effort: a PCT outage never blocks closing a fully-managed pathway. */
    @Test
    void closingSucceedsLocallyEvenWhenPctContributionFails() {
        SurgicalComplicationPathwayEntity e = openPathway();
        e.setGradeCode("I");
        e.setOwnedBy("Ward Team");
        e.setDisclosedBy("dr-x");
        e.setDisclosedAt(java.time.OffsetDateTime.now());
        when(repository.findByIdAndTenantId(pathwayId, tenant)).thenReturn(Optional.of(e));
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(episode()));
        when(pctClient.contributeCondition(any(), any(), any(), any(), any(), any()))
                .thenReturn(ContributionResult.unavailable());

        Map<String, Object> result = service.close(pathwayId, Map.of("outcome", "Settled with observation"));

        assertEquals("CLOSED", result.get("status"));
        assertNull(result.get("pctProblemRef"));
    }

    @Test
    void gradingAnUnknownPathwayIsNotFound() {
        when(repository.findByIdAndTenantId(pathwayId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.grade(pathwayId, "I"));
        assertEquals("COMPLICATION_PATHWAY_NOT_FOUND", ex.getCode());
    }
}
