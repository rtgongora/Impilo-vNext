package zw.gov.mohcc.impilo.tuso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.core.CssdCycleService;
import zw.gov.mohcc.impilo.tuso.core.CssdCycleStateMachine;
import zw.gov.mohcc.impilo.tuso.core.CssdCycleStateMachine.Action;
import zw.gov.mohcc.impilo.tuso.core.CssdCycleStateMachine.State;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InstrumentSetCssdCycleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InstrumentSetEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InstrumentSetCssdCycleRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.InstrumentSetRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Unit tests for the CSSD sterilisation state machine and its enforcement in {@link CssdCycleService}. */
class CssdCycleServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID SET_ID = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    private InstrumentSetRepository sets;
    private InstrumentSetCssdCycleRepository cycles;
    private EventOutboxRepository outbox;
    private CssdCycleService service;

    @BeforeEach
    void setUp() {
        sets = mock(InstrumentSetRepository.class);
        cycles = mock(InstrumentSetCssdCycleRepository.class);
        outbox = mock(EventOutboxRepository.class);
        service = new CssdCycleService(sets, cycles, outbox);
        TrustContextHolder.set(new TrustContext(TENANT, "cssd-op-1", "STAFF", "STERILE_SERVICES",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        when(sets.save(any(InstrumentSetEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(cycles.save(any(InstrumentSetCssdCycleEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    // ── pure state machine ─────────────────────────────────────────────────────────────────────────
    @Test
    void stateMachineWalksTheFullDecontaminationCycle() {
        State s = CssdCycleStateMachine.transition(State.STERILE, Action.ISSUE);
        assertThat(s).isEqualTo(State.IN_USE);
        s = CssdCycleStateMachine.transition(s, Action.RETURN);
        assertThat(s).isEqualTo(State.SOILED);
        s = CssdCycleStateMachine.transition(s, Action.REPROCESS);
        assertThat(s).isEqualTo(State.REPROCESSING);
        s = CssdCycleStateMachine.transition(s, Action.STERILISE);
        assertThat(s).isEqualTo(State.STERILISED);
        s = CssdCycleStateMachine.transition(s, Action.RELEASE);
        assertThat(s).isEqualTo(State.STERILE);
    }

    @Test
    void stateMachineRejectsIssuingANonSterileSet() {
        assertThatThrownBy(() -> CssdCycleStateMachine.transition(State.IN_USE, Action.ISSUE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CssdCycleStateMachine.transition(State.SOILED, Action.ISSUE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(CssdCycleStateMachine.canTransition(State.STERILE, Action.ISSUE)).isTrue();
    }

    // ── service enforcement ────────────────────────────────────────────────────────────────────────
    @Test
    void issueRequiresASterileSetAnd409sOtherwise() {
        InstrumentSetEntity inUse = set("IN_USE");
        when(sets.findByTenantIdAndId(TENANT, SET_ID)).thenReturn(Optional.of(inUse));

        assertThatThrownBy(() -> service.issueToCase(SET_ID, Map.of("episodeRef", "ep-1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verify(cycles, never()).save(any());
    }

    @Test
    void issueSterileSetMovesToInUseAndOpensCycle() {
        InstrumentSetEntity sterile = set("STERILE");
        when(sets.findByTenantIdAndId(TENANT, SET_ID)).thenReturn(Optional.of(sterile));
        when(cycles.countByTenantIdAndInstrumentSetId(TENANT, SET_ID)).thenReturn(0L);

        Map<String, Object> out = service.issueToCase(SET_ID, Map.of("episodeRef", "ep-42", "issuedTo", "Theatre 1"));

        assertThat(sterile.getSterilisationState()).isEqualTo("IN_USE");
        assertThat(out.get("sterilisationState")).isEqualTo("IN_USE");
        verify(cycles).save(any(InstrumentSetCssdCycleEntity.class));
        verify(outbox).save(any());
    }

    @Test
    void reprocessSterilisesTheSetAndStampsSterileUntil() {
        InstrumentSetEntity soiled = set("SOILED");
        when(sets.findByTenantIdAndId(TENANT, SET_ID)).thenReturn(Optional.of(soiled));
        InstrumentSetCssdCycleEntity cycle = new InstrumentSetCssdCycleEntity();
        cycle.setInstrumentSetId(SET_ID);
        cycle.setCycleNo(1);
        cycle.setCycleState("SOILED");
        when(cycles.findFirstByTenantIdAndInstrumentSetIdOrderByCycleNoDesc(TENANT, SET_ID))
                .thenReturn(Optional.of(cycle));

        Map<String, Object> out = service.reprocess(SET_ID, Map.of("autoclaveRef", "AC-LOAD-9", "operator", "op-2"));

        assertThat(soiled.getSterilisationState()).isEqualTo("STERILE");
        assertThat(soiled.getSterileUntil()).isNotNull();
        assertThat(cycle.getCycleState()).isEqualTo("STERILE");
        assertThat(cycle.getAutoclaveRef()).isEqualTo("AC-LOAD-9");
        assertThat(out.get("sterilisationState")).isEqualTo("STERILE");
    }

    @Test
    void reprocessWithoutAutoclaveRefIs400() {
        InstrumentSetEntity soiled = set("SOILED");
        when(sets.findByTenantIdAndId(TENANT, SET_ID)).thenReturn(Optional.of(soiled));
        when(cycles.findFirstByTenantIdAndInstrumentSetIdOrderByCycleNoDesc(TENANT, SET_ID))
                .thenReturn(Optional.of(new InstrumentSetCssdCycleEntity()));

        assertThatThrownBy(() -> service.reprocess(SET_ID, Map.of()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    private static InstrumentSetEntity set(String state) {
        InstrumentSetEntity s = new InstrumentSetEntity();
        s.setTenantId(TENANT);
        s.setFacilityId(1L);
        s.setName("General Surgery Set 1");
        s.setSterilisationState(state);
        return s;
    }
}
