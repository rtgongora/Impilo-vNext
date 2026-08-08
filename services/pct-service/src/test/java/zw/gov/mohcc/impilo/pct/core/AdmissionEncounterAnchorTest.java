package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.AdmissionRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@code admissions.encounter_id} must be a real {@code pct_encounters.encounter_ref}.
 *
 * <p>It is handed to inpatient-service on approval and becomes the census parent key; inpatient
 * then writes it into the clinical record as {@code "Encounter/" + encounterId} on discharge
 * summaries, operative notes, specimens and pathology references. It used to be whatever the API
 * caller supplied, stored unvalidated, and nothing in PCT ever copied the real {@code encounter_ref}
 * into it — so every FHIR reference inpatient built from it dangled, and BUTANO rejects a dangling
 * reference with HAPI-1094.</p>
 *
 * <p>The rule these tests pin: derive from the journey, honour a supplied value only when it
 * genuinely belongs to that journey, and never refuse the admission over a bad reference — a
 * patient not being admitted is worse than a record linkage gap.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdmissionEncounterAnchorTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String JOURNEY = "01J8XKQ9ZC4T7N2M5P8R3V6W1A";
    private static final UUID WARD = UUID.randomUUID();

    @Mock private AdmissionRepository admissionRepository;
    @Mock private JourneyRepository journeyRepository;
    @Mock private JourneyStateMachine journeyStateMachine;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private TelemetryService telemetryService;
    @Mock private EncounterRepository encounterRepository;

    private AdmissionWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new AdmissionWorkflow(admissionRepository, journeyRepository, journeyStateMachine,
                outboxRepository, telemetryService, new ObjectMapper(), encounterRepository);

        JourneyEntity journey = new JourneyEntity();
        journey.setJourneyId(JOURNEY);
        journey.setTenantId(TENANT);
        journey.setPatientCpid("c08ba747-26ff-4f19-b712-76561505e274");
        journey.setFacilityId(UUID.randomUUID());
        when(journeyRepository.findByJourneyId(JOURNEY)).thenReturn(Optional.of(journey));
        when(admissionRepository.save(any(AdmissionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private static EncounterEntity encounter(UUID ref, String status) {
        EncounterEntity e = new EncounterEntity();
        e.setEncounterRef(ref);
        e.setStatus(status);
        e.setJourneyId(JOURNEY);
        e.setTenantId(TENANT);
        return e;
    }

    private UUID admitAndReadAnchor(UUID supplied) {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(new TrustContext(
                    TENANT, "actor-1", "PROVIDER", "TREATMENT", null, UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL));
            AdmissionEntity admission =
                    workflow.requestAdmission(JOURNEY, WARD, null, supplied, null, "ELECTIVE");
            return admission.getEncounterId();
        }
    }

    @Test
    void derivesTheOpenEncounterWhenNoneIsSupplied() {
        UUID open = UUID.randomUUID();
        when(encounterRepository.findByTenantIdAndJourneyId(TENANT, JOURNEY))
                .thenReturn(List.of(encounter(UUID.randomUUID(), "COMPLETED"), encounter(open, "STARTED")));

        assertThat(admitAndReadAnchor(null))
                .describedAs("the visit still open is the one an admission belongs to")
                .isEqualTo(open);
    }

    @Test
    void honoursASuppliedRefThatBelongsToTheJourney() {
        UUID supplied = UUID.randomUUID();
        when(encounterRepository.findByTenantIdAndJourneyId(TENANT, JOURNEY))
                .thenReturn(List.of(encounter(supplied, "STARTED")));

        assertThat(admitAndReadAnchor(supplied)).isEqualTo(supplied);
    }

    @Test
    void replacesASuppliedRefThatBelongsToNoEncounterOnThisJourney() {
        // This is the defect: the caller's value was stored verbatim, so inpatient wrote
        // "Encounter/<something that does not exist>" into the clinical record.
        UUID bogus = UUID.randomUUID();
        UUID real = UUID.randomUUID();
        when(encounterRepository.findByTenantIdAndJourneyId(TENANT, JOURNEY))
                .thenReturn(List.of(encounter(real, "STARTED")));

        assertThat(admitAndReadAnchor(bogus))
                .describedAs("a reference that resolves to nothing must not reach inpatient")
                .isEqualTo(real);
    }

    @Test
    void fallsBackToTheMostRecentWhenEveryEncounterIsClosed() {
        UUID first = UUID.randomUUID();
        UUID last = UUID.randomUUID();
        when(encounterRepository.findByTenantIdAndJourneyId(TENANT, JOURNEY))
                .thenReturn(List.of(encounter(first, "COMPLETED"), encounter(last, "COMPLETED")));

        assertThat(admitAndReadAnchor(null)).isEqualTo(last);
    }

    @Test
    void admitsWithNoAnchorRatherThanAFabricatedOne_whenTheJourneyHasNoEncounter() {
        when(encounterRepository.findByTenantIdAndJourneyId(TENANT, JOURNEY))
                .thenReturn(List.of());

        assertThat(admitAndReadAnchor(UUID.randomUUID()))
                .describedAs("null is honest; a made-up UUID would dangle in the record forever")
                .isNull();
    }

    @Test
    void neverRefusesTheAdmission_evenWithAnUnresolvableReference() {
        // Blocking a patient from being admitted over a bad reference trades a record linkage gap
        // for clinical harm, which is the wrong way round.
        when(encounterRepository.findByTenantIdAndJourneyId(TENANT, JOURNEY))
                .thenReturn(List.of());

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(new TrustContext(
                    TENANT, "actor-1", "PROVIDER", "TREATMENT", null, UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL));

            AdmissionEntity admission = workflow.requestAdmission(
                    JOURNEY, WARD, null, UUID.randomUUID(), "Severe malaria", "EMERGENCY");

            assertThat(admission).isNotNull();
            assertThat(admission.getStatus()).isEqualTo("REQUESTED");
            assertThat(admission.getAdmittingDiagnosis()).isEqualTo("Severe malaria");
        }
    }
}
