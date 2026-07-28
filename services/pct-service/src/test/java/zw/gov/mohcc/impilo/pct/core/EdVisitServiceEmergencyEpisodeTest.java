package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.persistence.entity.*;
import zw.gov.mohcc.impilo.pct.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * W15's episode-spine reachability fix: EdVisitService (the actual ED registration path — the
 * dominant real-world emergency entry route) now mints an emergency_episode alongside every visit,
 * and closes it alongside every ed_visit disposition. Before this, nothing that registers an ED
 * walk-in/ambulance arrival ever called EmergencyEpisodeService.open() — the whole spine (alerts,
 * 15-disposition gate, handshake, command view, identity link) was invisible for ordinary ED care.
 *
 * <p>{@code EdVisitService} has no other unit test coverage at all — its only existing test,
 * {@code EdVisitIT}, is a {@code *IT.java} excluded from surefire in this estate. This is the sole
 * test that actually runs and proves the new wiring.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EdVisitServiceEmergencyEpisodeTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID FACILITY = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Mock private JourneyStateMachine journeyStateMachine;
    @Mock private TriageService triageService;
    @Mock private EncounterService encounterService;
    @Mock private DischargeWorkflow dischargeWorkflow;
    @Mock private EdVisitRepository visitRepository;
    @Mock private EdTriageAssessmentRepository triageAssessmentRepository;
    @Mock private EdTraumaActivationRepository traumaRepository;
    @Mock private EdTraumaSurveyRepository traumaSurveyRepository;
    @Mock private EdDispositionRepository dispositionRepository;
    @Mock private EdClinicalPageRepository pageRepository;
    @Mock private EmergencyCaseRepository emergencyCaseRepository;
    @Mock private JourneyRepository journeyRepository;
    @Mock private zw.gov.mohcc.impilo.pct.integration.DaidzaiEpisodeClient daidzaiEpisodes;
    @Mock private EdTraumaTeamService traumaTeamService;
    @Mock private EmergencyEpisodeService emergencyEpisodeService;
    @Mock private EmergencyDispositionService emergencyDispositionService;

    private EdVisitService service;

    @BeforeEach
    void setUp() {
        service = new EdVisitService(journeyStateMachine, triageService, encounterService, dischargeWorkflow,
                visitRepository, triageAssessmentRepository, traumaRepository, traumaSurveyRepository,
                dispositionRepository, pageRepository, emergencyCaseRepository, journeyRepository,
                new ObjectMapper(), daidzaiEpisodes, traumaTeamService, emergencyEpisodeService,
                emergencyDispositionService);
        TrustContextHolder.set(new TrustContext(TENANT, "nurse-A", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), FACILITY, null, null, AccessMode.INTERNAL));

        doAnswer(inv -> {
            EdVisitEntity v = inv.getArgument(0);
            if (v.getVisitId() == null) v.setVisitId(UUID.randomUUID());
            return v;
        }).when(visitRepository).save(any());
        // Default findById: echo back whatever was last saved via the visitId argument, so tests
        // that don't care about the visitDetail() return value at the end of openVisit() need no
        // extra stubbing. Tests that DO care override this per-test.
        when(visitRepository.findById(any())).thenAnswer(inv -> {
            EdVisitEntity v = new EdVisitEntity();
            v.setVisitId(inv.getArgument(0));
            v.setFacilityId(FACILITY);
            v.setJourneyId("J-DEFAULT");
            v.setPatientCpid("CPID-DEFAULT");
            return Optional.of(v);
        });
        when(triageAssessmentRepository.findByVisitIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(traumaRepository.findByVisitIdOrderByActivatedAtDesc(any())).thenReturn(List.of());
        when(traumaRepository.findFirstByVisitIdAndStatusOrderByActivatedAtDesc(any(), anyString())).thenReturn(Optional.empty());
        when(pageRepository.findByVisitIdOrderBySentAtDesc(any())).thenReturn(List.of());
        when(dispositionRepository.findByVisitId(any())).thenReturn(Optional.empty());
    }

    private JourneyEntity journey(String journeyId) {
        JourneyEntity j = new JourneyEntity();
        j.setJourneyId(journeyId);
        return j;
    }

    private EmergencyEpisodeService.OpenEpisodeResult episodeResult(UUID episodeId) {
        EmergencyEpisodeEntity e = new EmergencyEpisodeEntity();
        e.setEpisodeId(episodeId);
        return new EmergencyEpisodeService.OpenEpisodeResult(e, true, List.of());
    }

    @Test
    @DisplayName("registering a WALK_IN visit mints an emergency_episode with entry_route=WALK_IN, idempotent on the visit id")
    void openVisitMintsEpisode() {
        when(journeyStateMachine.createJourney(eq(FACILITY), eq("CPID-1"), eq("EMERGENCY"), any()))
                .thenReturn(journey("J-1"));
        UUID episodeId = UUID.randomUUID();
        when(emergencyEpisodeService.open(any())).thenReturn(episodeResult(episodeId));
        // requireVisit is called by visitDetail() at the end — return the just-saved visit.
        when(visitRepository.findById(any())).thenAnswer(inv -> {
            EdVisitEntity v = new EdVisitEntity();
            v.setVisitId(inv.getArgument(0));
            v.setFacilityId(FACILITY);
            v.setJourneyId("J-1");
            v.setPatientCpid("CPID-1");
            v.setEmergencyEpisodeId(episodeId);
            return Optional.of(v);
        });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("facilityId", FACILITY.toString());
        body.put("patientCpid", "CPID-1");
        body.put("arrivalMode", "WALK_IN");
        Map<String, Object> result = service.openVisit(body);

        ArgumentCaptor<EmergencyEpisodeService.OpenEpisodeCommand> captor =
                ArgumentCaptor.forClass(EmergencyEpisodeService.OpenEpisodeCommand.class);
        verify(emergencyEpisodeService).open(captor.capture());
        EmergencyEpisodeService.OpenEpisodeCommand cmd = captor.getValue();
        assertThat(cmd.entryRoute()).isEqualTo("WALK_IN");
        assertThat(cmd.entrySourceService()).isEqualTo("pct-service");
        assertThat(cmd.subjectCpid()).isEqualTo("CPID-1");
        assertThat(cmd.journeyId()).isEqualTo("J-1");
        assertThat(result.get("emergency_episode_id")).isEqualTo(episodeId.toString());

        // The saved visit was stamped with the minted episode id.
        ArgumentCaptor<EdVisitEntity> savedVisit = ArgumentCaptor.forClass(EdVisitEntity.class);
        verify(visitRepository, atLeastOnce()).save(savedVisit.capture());
        assertThat(savedVisit.getAllValues().stream().anyMatch(v -> episodeId.equals(v.getEmergencyEpisodeId()))).isTrue();
    }

    @Test
    @DisplayName("an unrecognised arrival mode maps to UNKNOWN_OR_UNRESPONSIVE, never guessed at")
    void unrecognisedArrivalModeMapsToUnknown() {
        when(journeyStateMachine.createJourney(any(), any(), any(), any())).thenReturn(journey("J-2"));
        when(emergencyEpisodeService.open(any())).thenReturn(episodeResult(UUID.randomUUID()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("facilityId", FACILITY.toString());
        body.put("patientCpid", "CPID-2");
        body.put("arrivalMode", "SOMETHING_LEGACY_NOBODY_MAPPED");
        service.openVisit(body);

        ArgumentCaptor<EmergencyEpisodeService.OpenEpisodeCommand> captor =
                ArgumentCaptor.forClass(EmergencyEpisodeService.OpenEpisodeCommand.class);
        verify(emergencyEpisodeService).open(captor.capture());
        assertThat(captor.getValue().entryRoute()).isEqualTo("UNKNOWN_OR_UNRESPONSIVE");
    }

    @Test
    @DisplayName("AMBULANCE arrival mode passes through unchanged — it already agrees with the episode vocabulary")
    void ambulanceArrivalModePassesThrough() {
        when(journeyStateMachine.createJourney(any(), any(), any(), any())).thenReturn(journey("J-3"));
        when(emergencyEpisodeService.open(any())).thenReturn(episodeResult(UUID.randomUUID()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("facilityId", FACILITY.toString());
        body.put("patientCpid", "CPID-3");
        body.put("arrivalMode", "AMBULANCE");
        service.openVisit(body);

        ArgumentCaptor<EmergencyEpisodeService.OpenEpisodeCommand> captor =
                ArgumentCaptor.forClass(EmergencyEpisodeService.OpenEpisodeCommand.class);
        verify(emergencyEpisodeService).open(captor.capture());
        assertThat(captor.getValue().entryRoute()).isEqualTo("AMBULANCE");
    }

    private EdVisitEntity visitWithEpisode(UUID episodeId) {
        EdVisitEntity v = new EdVisitEntity();
        v.setVisitId(UUID.randomUUID());
        v.setTenantId(TENANT);
        v.setFacilityId(FACILITY);
        v.setJourneyId("J-4");
        v.setPatientCpid("CPID-4");
        v.setStatus("IN_TREATMENT");
        v.setEmergencyEpisodeId(episodeId);
        return v;
    }

    @Test
    @DisplayName("recording DISCHARGE maps to the episode's DISCHARGED_HOME and closes the real episode disposition")
    void dischargeMapsToDischargedHome() {
        UUID episodeId = UUID.randomUUID();
        EdVisitEntity visit = visitWithEpisode(episodeId);
        when(visitRepository.findById(visit.getVisitId())).thenReturn(Optional.of(visit));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dispositionType", "DISCHARGE");
        body.put("dischargeSummary", "Stable, discharged with advice");
        body.put("dispositionBy", "dr-x");
        service.recordDisposition(visit.getVisitId(), body);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(emergencyDispositionService).record(eq(episodeId), eq(TENANT), captor.capture());
        assertThat(captor.getValue().get("dispositionType")).isEqualTo("DISCHARGED_HOME");
        assertThat(captor.getValue().get("dispositionReason")).isEqualTo("Stable, discharged with advice");
    }

    @Test
    @DisplayName("recording DEATH always supplies causeOrCircumstances so the episode's DIED gate is satisfiable")
    void deathSuppliesCauseOrCircumstances() {
        UUID episodeId = UUID.randomUUID();
        EdVisitEntity visit = visitWithEpisode(episodeId);
        when(visitRepository.findById(visit.getVisitId())).thenReturn(Optional.of(visit));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dispositionType", "DEATH");
        body.put("dispositionBy", "dr-x");
        service.recordDisposition(visit.getVisitId(), body);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(emergencyDispositionService).record(eq(episodeId), eq(TENANT), captor.capture());
        assertThat(captor.getValue().get("dispositionType")).isEqualTo("DIED");
        assertThat(captor.getValue().get("causeOrCircumstances")).isNotNull();
    }

    @Test
    @DisplayName("LWBS supplies a real lastSeenAt (the recording moment) so the episode's gate is satisfiable")
    void lwbsSuppliesLastSeenAt() {
        UUID episodeId = UUID.randomUUID();
        EdVisitEntity visit = visitWithEpisode(episodeId);
        when(visitRepository.findById(visit.getVisitId())).thenReturn(Optional.of(visit));
        when(journeyRepository.findByJourneyId(visit.getJourneyId())).thenReturn(Optional.of(journey(visit.getJourneyId())));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dispositionType", "LWBS");
        body.put("dispositionBy", "nurse-A");
        service.recordDisposition(visit.getVisitId(), body);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(emergencyDispositionService).record(eq(episodeId), eq(TENANT), captor.capture());
        assertThat(captor.getValue().get("dispositionType")).isEqualTo("LEFT_BEFORE_ASSESSMENT");
        assertThat(captor.getValue().get("lastSeenAt")).isNotNull();
    }

    @Test
    @DisplayName("a rejected episode disposition (missing required field) is logged, not thrown — the ed_visit disposition still succeeds")
    void episodeDispositionFailureDoesNotBlockEdVisitDisposition() {
        UUID episodeId = UUID.randomUUID();
        EdVisitEntity visit = visitWithEpisode(episodeId);
        when(visitRepository.findById(visit.getVisitId())).thenReturn(Optional.of(visit));
        when(emergencyDispositionService.record(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "destination is required"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dispositionType", "ADMIT");
        body.put("dispositionBy", "dr-x");
        // No destination supplied — the episode call is expected to fail internally, but the
        // ed_visit disposition itself must not throw.
        Map<String, Object> result = service.recordDisposition(visit.getVisitId(), body);

        assertThat(result).isNotNull();
        verify(emergencyDispositionService).record(eq(episodeId), eq(TENANT), any());
    }

    @Test
    @DisplayName("a visit with no minted episode (registered before this wave) skips the episode disposition entirely")
    void noEpisodeIdSkipsEpisodeDisposition() {
        EdVisitEntity visit = visitWithEpisode(null);
        visit.setEmergencyEpisodeId(null);
        when(visitRepository.findById(visit.getVisitId())).thenReturn(Optional.of(visit));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dispositionType", "DISCHARGE");
        body.put("dispositionBy", "dr-x");
        service.recordDisposition(visit.getVisitId(), body);

        verify(emergencyDispositionService, never()).record(any(), any(), any());
    }
}
