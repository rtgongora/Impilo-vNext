package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

    @Mock private EncounterRepository encounterRepository;
    @Mock private JourneyRepository journeyRepository;
    @Mock private JourneyStateMachine journeyStateMachine;
    @Mock private EventOutboxRepository outboxRepository;

    private EncounterService encounterService;

    @BeforeEach
    void setUp() {
        encounterService = new EncounterService(
                encounterRepository,
                journeyRepository,
                journeyStateMachine,
                outboxRepository,
                new ObjectMapper());
    }

    @Test
    void startEncounterStoresContextMetadata() {
        UUID tenantId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        TrustContext context = new TrustContext(
                tenantId, "provider-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), facilityId, workspaceId, null, AccessMode.INTERNAL);

        JourneyEntity journey = new JourneyEntity();
        journey.setJourneyId("J-1");
        journey.setPatientCpid("CPID-1");
        journey.setState(JourneyState.QUEUED);

        when(journeyRepository.findByJourneyId("J-1")).thenReturn(Optional.of(journey));
        when(encounterRepository.findByTenantIdAndJourneyId(tenantId, "J-1")).thenReturn(List.of());
        when(encounterRepository.save(any(EncounterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            EncounterEntity created = encounterService.startEncounter(
                    "J-1", "TELEHEALTH", "virtual", "virtual_request", "virtual", "video",
                    "virtual", "urgent", "P2", "PATH-ANC-01", "PROTO-ED-01");

            assertThat(created.getEncounterContext()).isEqualTo("virtual");
            assertThat(created.getEntryPoint()).isEqualTo("virtual_request");
            assertThat(created.getCareSetting()).isEqualTo("virtual");
            assertThat(created.getPriority()).isEqualTo("urgent");
            assertThat(created.getTriageCategory()).isEqualTo("P2");
            assertThat(created.getPathwayRef()).isEqualTo("PATH-ANC-01");
            assertThat(created.getProtocolRef()).isEqualTo("PROTO-ED-01");
        }
    }

    @Test
    void startEncounterRejectsInvalidEntryPoint() {
        UUID tenantId = UUID.randomUUID();
        TrustContext context = new TrustContext(
                tenantId, "provider-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);

        JourneyEntity journey = new JourneyEntity();
        journey.setJourneyId("J-2");
        journey.setPatientCpid("CPID-2");
        journey.setState(JourneyState.QUEUED);

        when(journeyRepository.findByJourneyId("J-2")).thenReturn(Optional.of(journey));
        when(encounterRepository.findByTenantIdAndJourneyId(tenantId, "J-2")).thenReturn(List.of());

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            assertThatThrownBy(() -> encounterService.startEncounter(
                    "J-2", "CONSULTATION", "outpatient", "self_checkin_kiosk", "in_person", null,
                    "facility", "routine", null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid encounter entry point");
        }
    }

    @Test
    void startEncounterRejectsDuplicateActiveEncounter() {
        UUID tenantId = UUID.randomUUID();
        TrustContext context = new TrustContext(
                tenantId, "provider-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);

        JourneyEntity journey = new JourneyEntity();
        journey.setJourneyId("J-3");
        journey.setPatientCpid("CPID-3");
        journey.setState(JourneyState.IN_SERVICE);

        EncounterEntity active = new EncounterEntity();
        active.setStatus("STARTED");

        when(journeyRepository.findByJourneyId("J-3")).thenReturn(Optional.of(journey));
        when(encounterRepository.findByTenantIdAndJourneyId(tenantId, "J-3")).thenReturn(List.of(active));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            assertThatThrownBy(() -> encounterService.startEncounter(
                    "J-3", "CONSULTATION", "outpatient", "walk_in", "in_person", null,
                    "facility", "routine", null, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Encounter already active");
        }
    }

    @Test
    void startEncounterSupportsProcedureContexts() {
        UUID tenantId = UUID.randomUUID();
        TrustContext context = new TrustContext(
                tenantId, "provider-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);

        JourneyEntity journey = new JourneyEntity();
        journey.setJourneyId("J-4");
        journey.setPatientCpid("CPID-4");
        journey.setState(JourneyState.QUEUED);

        when(journeyRepository.findByJourneyId("J-4")).thenReturn(Optional.of(journey));
        when(encounterRepository.findByTenantIdAndJourneyId(tenantId, "J-4")).thenReturn(List.of());
        when(encounterRepository.save(any(EncounterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            EncounterEntity created = encounterService.startEncounter(
                    "J-4", "OPERATING_ROOM_CASE", "operating_room", "scheduled_appointment",
                    "in_person", null, "facility", "urgent", "P2", "PATH-OR-01", "PROTO-OR-01");

            assertThat(created.getEncounterContext()).isEqualTo("operating_room");
        }
    }

    @Test
    void updateEncounterPathwayProtocolUpdatesReferences() {
        UUID tenantId = UUID.randomUUID();
        TrustContext context = new TrustContext(
                tenantId, "provider-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
        EncounterEntity encounter = new EncounterEntity();
        encounter.setEncounterRef(UUID.randomUUID());
        encounter.setJourneyId("J-5");
        encounter.setPathwayRef("OLD-PATH");
        encounter.setProtocolRef("OLD-PROTO");

        when(encounterRepository.findById(55L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(EncounterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            EncounterEntity updated = encounterService.updateEncounterPathwayProtocol(
                    55L, "PATH-SEPSIS-01", "PROTO-CRIT-01");

            assertThat(updated.getPathwayRef()).isEqualTo("PATH-SEPSIS-01");
            assertThat(updated.getProtocolRef()).isEqualTo("PROTO-CRIT-01");
        }
    }
}
