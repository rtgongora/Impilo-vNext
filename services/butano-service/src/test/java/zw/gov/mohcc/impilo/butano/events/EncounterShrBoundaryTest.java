package zw.gov.mohcc.impilo.butano.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Encounter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The facility visit's boundary into the shared health record.
 *
 * <p>The SHR held zero Encounters, and that absence was load-bearing rather than cosmetic: BUTANO
 * enforces referential integrity on write, so every Procedure, DocumentReference and
 * DiagnosticReport naming {@code Encounter/<something>} was rejected with HAPI-1094. Theatre's
 * operative notes and oros's lab reports had nowhere to land because the anchor did not exist.</p>
 *
 * <p>Companion to {@link ObservationShrBoundaryTest} and {@link ConditionShrBoundaryTest}. The same
 * standing rule applies — identifying data stays in VITO, the SHR holds a CPID — and these tests
 * exercise the one method that decides what crosses.</p>
 */
class EncounterShrBoundaryTest {

    private static final String TENANT_TAG = "https://impilo.gov.zw/tenant";
    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String ENCOUNTER_REF = "8f14e45f-ceea-467a-9b8a-1f2c3d4e5f60";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode payload(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Encounter build(String json) {
        return ButanoEventConsumer.buildEncounter(TENANT_TAG, TENANT, "1", ENCOUNTER_REF, payload(json));
    }

    // ── The anchor itself ─────────────────────────────────────────────────────────────────────

    @Test
    void carriesTheEncounterRefAsItsBusinessIdentifier() {
        Encounter e = build("{\"encounterContext\":\"outpatient\",\"startedAt\":\"2026-08-08T09:00:00Z\"}");

        assertThat(e.getIdentifierFirstRep().getSystem())
                .describedAs("oros, inpatient and daidzai all carry encounter_ref; a match URL on "
                        + "this system is what makes their references resolvable")
                .isEqualTo("https://impilo.gov.zw/pct/encounter-id");
        assertThat(e.getIdentifierFirstRep().getValue()).isEqualTo(ENCOUNTER_REF);
    }

    @Test
    void isTenantTaggedAndSubjectIsTheResolvedPatient() {
        Encounter e = build("{\"encounterContext\":\"outpatient\"}");

        assertThat(e.getMeta().getTagFirstRep().getSystem()).isEqualTo(TENANT_TAG);
        assertThat(e.getMeta().getTagFirstRep().getCode()).isEqualTo(TENANT.toString());
        assertThat(e.getSubject().getReference())
                .describedAs("the resolved HAPI logical id, never the CPID — a CPID is not a "
                        + "FHIR logical id")
                .isEqualTo("Patient/1");
    }

    // ── Status is derived from the payload, not the topic ─────────────────────────────────────

    @Test
    void anEncounterWithNoEndTimeIsInProgress() {
        Encounter e = build("{\"encounterContext\":\"outpatient\",\"startedAt\":\"2026-08-08T09:00:00Z\"}");

        assertThat(e.getStatus()).isEqualTo(Encounter.EncounterStatus.INPROGRESS);
    }

    @Test
    void anEncounterWithAnEndTimeIsFinished_whicheverTopicDeliveredIt() {
        // The completed event is not guaranteed to arrive second, or at all. Deriving status from
        // the payload rather than the topic is what stops a replay reopening a closed visit.
        Encounter e = build("{\"encounterContext\":\"outpatient\",\"startedAt\":\"2026-08-08T09:00:00Z\","
                + "\"endedAt\":\"2026-08-08T09:45:00Z\"}");

        assertThat(e.getStatus()).isEqualTo(Encounter.EncounterStatus.FINISHED);
        assertThat(e.getPeriod().hasStart()).isTrue();
        assertThat(e.getPeriod().hasEnd()).isTrue();
    }

    @Test
    void aZeroSecondTimestampSurvives() {
        // Java renders 09:00:00 as "2026-08-08T09:00Z", which FHIR rejects for missing seconds.
        // Normalising through OffsetDateTime is why the period is not silently dropped.
        Encounter e = build("{\"encounterContext\":\"outpatient\",\"startedAt\":\"2026-08-08T09:00:00Z\"}");

        assertThat(e.getPeriod().hasStart()).isTrue();
    }

    @Test
    void anUnparseableTimestampIsOmitted_notGuessed() {
        Encounter e = build("{\"encounterContext\":\"outpatient\",\"startedAt\":\"yesterday morning\"}");

        assertThat(e.getPeriod().hasStart()).isFalse();
        assertThat(e.getStatus())
                .describedAs("an unreadable start time must not change what the visit IS")
                .isEqualTo(Encounter.EncounterStatus.INPROGRESS);
    }

    // ── class comes from the closed vocabulary, not the free-text type ────────────────────────

    @ParameterizedTest
    @CsvSource({
            "outpatient,      AMB",
            "emergency,       EMER",
            "inpatient,       IMP",
            "community,       FLD",
            "virtual,         VR",
            "procedure,       AMB",
            "procedure_room,  AMB",
            "operating_room,  AMB",
    })
    void mapsPctsClosedContextVocabularyOntoTheRequiredFhirClass(String context, String expected) {
        Encounter e = build("{\"encounterContext\":\"" + context + "\"}");

        assertThat(e.getClass_().getSystem())
                .isEqualTo("http://terminology.hl7.org/CodeSystem/v3-ActCode");
        assertThat(e.getClass_().getCode()).isEqualTo(expected);
    }

    @Test
    void aVirtualModalityWinsOverTheContext() {
        // A consultation delivered remotely is a virtual encounter whatever the clinic recorded.
        Encounter e = build("{\"encounterContext\":\"outpatient\",\"modality\":\"virtual\"}");

        assertThat(e.getClass_().getCode()).isEqualTo("VR");
    }

    @Test
    void anUnknownContextStillProducesAClass_andKeepsTheRawValue() {
        // FHIR requires class. An unrecognised context means PCT's vocabulary grew and this
        // mapping did not — the visit must still be archived, and the raw value must survive so
        // the gap is fixable from the record.
        Encounter e = build("{\"encounterContext\":\"day_surgery_unit\"}");

        assertThat(e.getClass_().getCode()).isEqualTo("AMB");
        assertThat(e.getType()).anySatisfy(t ->
                assertThat(t.getCodingFirstRep().getCode()).isEqualTo("day_surgery_unit"));
    }

    @Test
    void bothPctVocabulariesArePreservedVerbatim() {
        Encounter e = build("{\"encounterContext\":\"emergency\",\"encounterType\":\"CONSULTATION\"}");

        assertThat(e.getType()).hasSize(2);
        assertThat(e.getType()).anySatisfy(t -> {
            assertThat(t.getCodingFirstRep().getSystem())
                    .isEqualTo("https://impilo.gov.zw/pct/encounter-context");
            assertThat(t.getCodingFirstRep().getCode()).isEqualTo("emergency");
        });
        assertThat(e.getType()).anySatisfy(t -> {
            assertThat(t.getCodingFirstRep().getSystem())
                    .isEqualTo("https://impilo.gov.zw/pct/encounter-type");
            assertThat(t.getCodingFirstRep().getCode()).isEqualTo("CONSULTATION");
        });
    }

    // ── No PII, and no dangling references ────────────────────────────────────────────────────

    @Test
    void providerFacilityAndWorkspaceAreNotWritten() {
        // Encounter.participant and Encounter.serviceProvider reference Practitioner and
        // Organization, and the SHR holds neither. Writing them would be a dangling reference —
        // or a free-text display carrying a person's name into a record that must hold none.
        Encounter e = build("{\"encounterContext\":\"outpatient\","
                + "\"assignedProvider\":\"Dr Tendai Moyo\","
                + "\"attendingProviderId\":\"prov-123\","
                + "\"facilityId\":\"11111111-1111-4111-8111-111111111111\","
                + "\"workspaceId\":\"22222222-2222-4222-8222-222222222222\","
                + "\"shiftId\":\"shift-9\"}");

        assertThat(e.hasParticipant()).isFalse();
        assertThat(e.hasServiceProvider()).isFalse();
        assertThat(e.hasLocation()).isFalse();

        String serialised = e.getType() + e.getIdentifier().toString() + e.getSubject().getReference();
        assertThat(serialised).doesNotContain("Tendai").doesNotContain("Moyo");
    }

    @Test
    void aPayloadWithNothingButTheAnchorStillArchives() {
        // The visit happened. A sparse event must not be dropped.
        Encounter e = build("{}");

        assertThat(e.getIdentifierFirstRep().getValue()).isEqualTo(ENCOUNTER_REF);
        assertThat(e.getSubject().getReference()).isEqualTo("Patient/1");
        assertThat(e.getStatus()).isEqualTo(Encounter.EncounterStatus.INPROGRESS);
        assertThat(e.getClass_().getCode()).isEqualTo("AMB");
        assertThat(e.hasPeriod()).isFalse();
    }

    @Test
    void snakeCaseAndCamelCasePayloadsBothWork() {
        // PCT's legacy topics and the v1.1 companion envelope do not agree on casing, and both
        // reach this consumer.
        Encounter snake = build("{\"encounter_context\":\"emergency\",\"ended_at\":\"2026-08-08T10:00:00Z\"}");

        assertThat(snake.getClass_().getCode()).isEqualTo("EMER");
        assertThat(snake.getStatus()).isEqualTo(Encounter.EncounterStatus.FINISHED);
    }
}
