package zw.gov.mohcc.impilo.inpatient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.inpatient.core.TheatreReadinessBoardService;
import zw.gov.mohcc.impilo.inpatient.core.TheatreService;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureConsentEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureEpisodeEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureBlockerResolutionRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureConsentRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureEpisodeRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureReadinessCheckRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TheatreReadinessBoardServiceTest {

    private TheatreService theatreService;
    private ProcedureEpisodeRepository episodeRepository;
    private ProcedureConsentRepository consentRepository;
    private ProcedureReadinessCheckRepository readinessRepository;
    private ProcedureBlockerResolutionRepository resolutionRepository;
    private TheatreReadinessBoardService service;
    private UUID episodeId;

    @BeforeEach
    void setUp() {
        theatreService = mock(TheatreService.class);
        episodeRepository = mock(ProcedureEpisodeRepository.class);
        consentRepository = mock(ProcedureConsentRepository.class);
        readinessRepository = mock(ProcedureReadinessCheckRepository.class);
        resolutionRepository = mock(ProcedureBlockerResolutionRepository.class);
        zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureInstrumentSetUseRepository instrumentSetUseRepository =
                mock(zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureInstrumentSetUseRepository.class);
        when(instrumentSetUseRepository.findByEpisodeIdAndIssueStatus(any(), any()))
                .thenReturn(java.util.List.of());
        service = new TheatreReadinessBoardService(theatreService, episodeRepository, consentRepository,
                readinessRepository, resolutionRepository, instrumentSetUseRepository, new ObjectMapper());
        episodeId = UUID.randomUUID();
        ProcedureEpisodeEntity episode = new ProcedureEpisodeEntity();
        episode.setStatus("BOOKED");
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(episode));
        when(theatreService.evaluateReadiness(any(), any()))
                .thenReturn(Map.of("checks", List.of(), "blockers", List.of(), "bookable", true));
        when(readinessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(resolutionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ProcedureConsentEntity consent(String type, String status, boolean withdrawn) {
        ProcedureConsentEntity c = new ProcedureConsentEntity();
        c.setEpisodeId(episodeId);
        c.setConsentType(type);
        c.setStatus(status);
        c.setRequired(true);
        c.setWithdrawn(withdrawn);
        return c;
    }

    @Test
    void allEvidenceSatisfiedAndConsentGrantedIsReady() {
        when(consentRepository.findByEpisodeId(episodeId)).thenReturn(List.of(
                consent("PROCEDURE", "GRANTED", false),
                consent("ANAESTHESIA", "GRANTED", false)));

        Map<String, Object> board = service.evaluateBoard(episodeId, Map.of(
                "bedAvailable", true, "recoveryBedAvailable", true, "instrumentSetSterile", true,
                "fastingConfirmed", true, "identityConfirmed", true, "prophylaxisGiven", true,
                "pregnancyChecked", true, "clearanceObtained", true));

        assertThat(board.get("board_state")).isEqualTo("Ready");
    }

    @Test
    void missingRecoveryBedBlocksBoard() {
        when(consentRepository.findByEpisodeId(episodeId)).thenReturn(List.of(consent("PROCEDURE", "GRANTED", false)));

        Map<String, Object> board = service.evaluateBoard(episodeId, Map.of(
                "recoveryBedAvailable", false, "fastingConfirmed", true, "identityConfirmed", true));

        assertThat(board.get("board_state")).isEqualTo("Blocked");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> blockers = (List<Map<String, String>>) board.get("blockers");
        assertThat(blockers).anyMatch(b -> "RECOVERY_NOT_READY".equals(b.get("code")));
    }

    @Test
    void withdrawnConsentBlocksBoard() {
        when(consentRepository.findByEpisodeId(episodeId)).thenReturn(List.of(
                consent("PROCEDURE", "GRANTED", false),
                consent("ANAESTHESIA", "WITHDRAWN", true)));

        Map<String, Object> board = service.evaluateBoard(episodeId, Map.of(
                "bedAvailable", true, "recoveryBedAvailable", true, "instrumentSetSterile", true,
                "fastingConfirmed", true, "identityConfirmed", true, "prophylaxisGiven", true,
                "pregnancyChecked", true, "clearanceObtained", true));

        assertThat(board.get("board_state")).isEqualTo("Blocked");
    }

    @Test
    void ungatedEvidenceYieldsReadyWithWarnings() {
        when(consentRepository.findByEpisodeId(episodeId)).thenReturn(List.of(consent("PROCEDURE", "GRANTED", false)));
        // No fasting/identity flags supplied → NOT_CHECKED → warnings.
        Map<String, Object> board = service.evaluateBoard(episodeId, Map.of(
                "bedAvailable", true, "recoveryBedAvailable", true, "instrumentSetSterile", true));
        assertThat(board.get("board_state")).isEqualTo("Ready-with-warnings");
    }

    @Test
    void resolveBlockerRoutesToOwner() {
        Map<String, Object> result = service.resolveBlocker(episodeId, Map.of("domain", "recovery"));
        assertThat(result.get("routed_owner")).isEqualTo("tuso");
        assertThat(result.get("domain")).isEqualTo("RECOVERY");
    }
}
