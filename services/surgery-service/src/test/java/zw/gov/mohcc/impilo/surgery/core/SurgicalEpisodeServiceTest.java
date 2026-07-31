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
import zw.gov.mohcc.impilo.surgery.api.dto.SurgicalEpisodeDtos.OpenEpisodeRequest;
import zw.gov.mohcc.impilo.surgery.api.dto.SurgicalEpisodeDtos.SurgicalEpisodeView;
import zw.gov.mohcc.impilo.surgery.integration.InpatientSpecimenClient;
import zw.gov.mohcc.impilo.surgery.integration.InpatientSpecimenClient.SpecimenGateView;
import zw.gov.mohcc.impilo.surgery.integration.PctProblemContributionClient;
import zw.gov.mohcc.impilo.surgery.integration.PctProblemContributionClient.ContributionResult;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalEpisodeEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalEpisodeRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S1 — the surgical episode. Proves the three things this class deliberately does (reference,
 * contribute best-effort, execute-never-decide) and the CC-5/CC-2-shaped refusals: no PCT anchor,
 * no operative indication, an invented status.
 */
@ExtendWith(MockitoExtension.class)
class SurgicalEpisodeServiceTest {

    @Mock SurgicalEpisodeRepository repository;
    @Mock PctProblemContributionClient pctClient;
    @Mock InpatientSpecimenClient specimenClient;
    @Mock EpisodeSpecialtyService episodeSpecialtyService;

    private SurgicalEpisodeService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID episodeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SurgicalEpisodeService(repository, pctClient, specimenClient, episodeSpecialtyService);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
        org.mockito.Mockito.lenient().when(repository.save(any())).thenAnswer(i -> {
            SurgicalEpisodeEntity e = i.getArgument(0);
            if (e.getId() == null) {
                e.setId(episodeId);
            }
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private OpenEpisodeRequest request() {
        return new OpenEpisodeRequest("CPID-1", "journey-1", null,
                "Symptomatic gallstones with recurrent biliary colic", "Trialled dietary modification without resolution",
                "Symptomatic gallstones", "CONFIRMED", "Ultrasound-confirmed", "GENERAL_SURGERY");
    }

    @Test
    void openEpisodeCreatesTheEpisodeAndContributesToPct() {
        when(pctClient.contributeCondition(eq("CPID-1"), eq("journey-1"), isNull(),
                eq("Symptomatic gallstones"), eq("CONFIRMED"), anyString()))
                .thenReturn(new ContributionResult(UUID.randomUUID(), true));

        SurgicalEpisodeView v = service.openEpisode(request());

        assertEquals("ASSESSMENT", v.status());
        assertEquals("CPID-1", v.subjectCpid());
        assertTrue(v.pctContributed());
        assertNotNull(v.pctProblemRef());
    }

    /** Best-effort: a PCT outage never blocks recording the episode locally. */
    @Test
    void openEpisodeSucceedsLocallyEvenWhenPctContributionFails() {
        when(pctClient.contributeCondition(any(), any(), any(), any(), any(), any()))
                .thenReturn(ContributionResult.unavailable());

        SurgicalEpisodeView v = service.openEpisode(request());

        assertEquals("ASSESSMENT", v.status(), "the episode is still recorded locally");
        assertFalse(v.pctContributed());
        assertNull(v.pctProblemRef());
    }

    @Test
    void openEpisodeWithNoPctAnchorAtAllIsRefused() {
        OpenEpisodeRequest req = new OpenEpisodeRequest("CPID-1", null, null,
                "Indication", null, null, null, null, null);

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class, () -> service.openEpisode(req));
        assertEquals("PCT_ANCHOR_REQUIRED", ex.getCode());
    }

    @Test
    void openEpisodeWithNoOperativeIndicationIsRefused() {
        OpenEpisodeRequest req = new OpenEpisodeRequest("CPID-1", "journey-1", null,
                "  ", null, null, null, null, null);

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class, () -> service.openEpisode(req));
        assertEquals("OPERATIVE_INDICATION_REQUIRED", ex.getCode());
    }

    @Test
    void openEpisodeWithNoSubjectIsRefused() {
        OpenEpisodeRequest req = new OpenEpisodeRequest("", "journey-1", null,
                "Indication", null, null, null, null, null);

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class, () -> service.openEpisode(req));
        assertEquals("SUBJECT_CPID_REQUIRED", ex.getCode());
    }

    /** No condition display supplied at all — nothing to contribute, so PCT is never called. */
    @Test
    void openEpisodeWithNoConditionDisplayNeverCallsPct() {
        OpenEpisodeRequest req = new OpenEpisodeRequest("CPID-1", "journey-1", null,
                "Indication", null, null, null, null, null);

        service.openEpisode(req);

        verify(pctClient, never()).contributeCondition(any(), any(), any(), any(), any(), any());
    }

    @Test
    void linkProcedureEpisodeStampsTheReference() {
        SurgicalEpisodeEntity e = entity();
        UUID procRef = UUID.randomUUID();
        when(repository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.of(e));

        SurgicalEpisodeView v = service.linkProcedureEpisode(episodeId, procRef);

        assertEquals(procRef.toString(), v.procedureEpisodeRef());
    }

    @Test
    void transitionToAnInventedStatusIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.transition(episodeId, "PROBABLY_FINE"));
        assertEquals("INVALID_STATUS", ex.getCode());
    }

    @Test
    void transitionToAValidStatusSucceeds() {
        SurgicalEpisodeEntity e = entity();
        when(repository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.of(e));

        SurgicalEpisodeView v = service.transition(episodeId, "LISTED_FOR_SURGERY");

        assertEquals("LISTED_FOR_SURGERY", v.status());
    }

    /** §16 histology closure gate: no linked operation at all means nothing to gate on. */
    @Test
    void closingAnEpisodeWithNoLinkedOperationNeedsNoSpecimenCheck() {
        SurgicalEpisodeEntity e = entity();
        when(repository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.of(e));

        SurgicalEpisodeView v = service.transition(episodeId, "CLOSED");

        assertEquals("CLOSED", v.status());
        verify(specimenClient, never()).specimenGate(any());
    }

    @Test
    void closingAnEpisodeWithAllSpecimensReviewedSucceeds() {
        SurgicalEpisodeEntity e = entity();
        UUID procRef = UUID.randomUUID();
        e.setProcedureEpisodeRef(procRef);
        when(repository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.of(e));
        when(specimenClient.specimenGate(procRef)).thenReturn(new SpecimenGateView(true, 2, 0));

        SurgicalEpisodeView v = service.transition(episodeId, "CLOSED");

        assertEquals("CLOSED", v.status());
    }

    @Test
    void closingAnEpisodeWithUnreviewedHistologyIsRefused() {
        SurgicalEpisodeEntity e = entity();
        UUID procRef = UUID.randomUUID();
        e.setProcedureEpisodeRef(procRef);
        when(repository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.of(e));
        when(specimenClient.specimenGate(procRef)).thenReturn(new SpecimenGateView(true, 2, 1));

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.transition(episodeId, "CLOSED"));
        assertEquals("HISTOLOGY_UNREVIEWED", ex.getCode());
    }

    /** Fail-SAFE: an unanswerable specimen question blocks closure, opposite to the PCT client's own posture. */
    @Test
    void closingAnEpisodeWhenSpecimenStateIsUnknownIsRefused() {
        SurgicalEpisodeEntity e = entity();
        UUID procRef = UUID.randomUUID();
        e.setProcedureEpisodeRef(procRef);
        when(repository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.of(e));
        when(specimenClient.specimenGate(procRef)).thenReturn(SpecimenGateView.UNKNOWN);

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.transition(episodeId, "CLOSED"));
        assertEquals("SPECIMEN_STATE_UNKNOWN", ex.getCode());
        assertEquals(503, ex.getStatus());
    }

    @Test
    void getEpisodeForAnUnknownIdIsNotFound() {
        when(repository.findByIdAndTenantId(episodeId, tenant)).thenReturn(Optional.empty());

        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class, () -> service.getEpisode(episodeId));
        assertEquals("SURGICAL_EPISODE_NOT_FOUND", ex.getCode());
        assertEquals(404, ex.getStatus());
    }

    private SurgicalEpisodeEntity entity() {
        SurgicalEpisodeEntity e = new SurgicalEpisodeEntity();
        e.setId(episodeId);
        e.setTenantId(tenant);
        e.setSubjectCpid("CPID-1");
        e.setJourneyId("journey-1");
        e.setOperativeIndication("Symptomatic gallstones");
        e.setStatus("ASSESSMENT");
        return e;
    }
}
