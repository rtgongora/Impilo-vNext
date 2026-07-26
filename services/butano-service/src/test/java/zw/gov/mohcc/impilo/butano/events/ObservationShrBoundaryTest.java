package zw.gov.mohcc.impilo.butano.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boundary between the clinical record and the shared health record.
 *
 * <p>The platform's standing rule is that identifying data stays in VITO and the SHR holds only a
 * CPID. These tests exercise the one method that decides what crosses, because a rule enforced only
 * by the producer is a rule that holds until someone changes the producer.</p>
 */
class ObservationShrBoundaryTest {

    private static final String TENANT_TAG = "https://impilo.gov.zw/tenant";
    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode payload(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Observation build(String json) {
        return ButanoEventConsumer.buildObservation(TENANT_TAG, TENANT, "42", "obs-1",
                "BODY_TEMPERATURE", payload(json));
    }

    // --- the boundary rule -------------------------------------------------------------------

    @Test
    void noPersonalIdentifiersCrossIntoTheSharedRecordEvenWhenThePayloadCarriesThem() {
        // A deliberately contaminated payload. The producer should never send these, but the
        // boundary must hold regardless — that is the difference between a rule and a convention.
        Observation obs = build("""
                {
                  "observation_id": "obs-1",
                  "subject_cpid": "CPID-123",
                  "code": "BODY_TEMPERATURE",
                  "value_quantity": 38.4,
                  "value_unit": "Cel",
                  "patient_name": "Tendai Moyo",
                  "date_of_birth": "2025-03-14",
                  "national_id": "63-1234567-A-42",
                  "phone": "+263771234567",
                  "address": "12 Samora Machel Ave, Harare",
                  "mother_name": "Rudo Moyo"
                }
                """);

        String serialised = obs.getIdentifierFirstRep().getValue()
                + " " + obs.getSubject().getReference()
                + " " + obs.getCode().getCodingFirstRep().getCode()
                + " " + String.valueOf(obs.getValue());

        assertThat(serialised)
                .doesNotContain("Tendai", "Moyo", "2025-03-14", "63-1234567", "+263771234567",
                        "Samora Machel", "Rudo");
        // The subject is a reference to a Patient resource, never an inline identity.
        assertThat(obs.getSubject().getReference()).isEqualTo("Patient/42");
        assertThat(obs.hasContained()).isFalse();
    }

    @Test
    void theResourceCarriesNoPatientNameOrBirthDateFields() {
        Observation obs = build("""
                {"observation_id":"obs-1","subject_cpid":"CPID-123","code":"BODY_TEMPERATURE",
                 "value_quantity":38.4,"value_unit":"Cel"}
                """);

        // An Observation has no place to put a name, and nothing here creates a contained Patient.
        assertThat(obs.getContained()).isEmpty();
        assertThat(obs.getSubject().hasDisplay())
                .as("a subject display would put a human-readable identity into the SHR")
                .isFalse();
    }

    @Test
    void everyResourceIsTaggedWithItsTenant() {
        Observation obs = build("""
                {"observation_id":"obs-1","subject_cpid":"CPID-123","code":"BODY_TEMPERATURE",
                 "value_quantity":38.4,"value_unit":"Cel"}
                """);

        assertThat(obs.getMeta().getTagFirstRep().getSystem()).isEqualTo(TENANT_TAG);
        assertThat(obs.getMeta().getTagFirstRep().getCode()).isEqualTo(TENANT.toString());
    }

    // --- the absent-value rule ------------------------------------------------------------------

    @Test
    void anObservationWithNoValueCarriesItsReasonRatherThanBeingBlank() {
        // The whole point of archiving it at all. "Nobody took the temperature" is a fact a
        // clinician at the next facility needs; a resource with neither value nor reason would
        // read as a completed measurement whose result went missing.
        Observation obs = build("""
                {"observation_id":"obs-1","subject_cpid":"CPID-123","code":"BODY_TEMPERATURE",
                 "data_absent_reason":"NOT_ASSESSED"}
                """);

        assertThat(obs.hasValue()).isFalse();
        assertThat(obs.getDataAbsentReason().getCodingFirstRep().getCode()).isEqualTo("not-assessed");
    }

    @Test
    void aPayloadWithNeitherValueNorReasonIsRecordedAsUnknownNotAsBlank() {
        Observation obs = build("""
                {"observation_id":"obs-1","subject_cpid":"CPID-123","code":"BODY_TEMPERATURE"}
                """);

        assertThat(obs.hasValue()).isFalse();
        assertThat(obs.getDataAbsentReason().getCodingFirstRep().getCode()).isEqualTo("unknown");
    }

    // --- value shapes ------------------------------------------------------------------------------

    @Test
    void aQuantityKeepsItsUnitBecauseANumberWithoutOneIsNotAMeasurement() {
        Observation obs = build("""
                {"observation_id":"obs-1","subject_cpid":"CPID-123","code":"BODY_TEMPERATURE",
                 "value_quantity":38.4,"value_unit":"Cel"}
                """);

        assertThat(obs.getValue()).isInstanceOf(Quantity.class);
        Quantity q = (Quantity) obs.getValue();
        assertThat(q.getValue().doubleValue()).isEqualTo(38.4);
        assertThat(q.getUnit()).isEqualTo("Cel");
    }

    @Test
    void aQuantityWithoutAUnitFallsThroughRatherThanBeingWrittenAsABareNumber() {
        Observation obs = build("""
                {"observation_id":"obs-1","subject_cpid":"CPID-123","code":"BODY_TEMPERATURE",
                 "value_quantity":38.4,"data_absent_reason":"ERROR"}
                """);

        // No value at all, rather than a bare number: 38.4 of what?
        assertThat(obs.hasValue()).isFalse();
        assertThat(obs.getDataAbsentReason().getCodingFirstRep().getCode()).isEqualTo("error");
    }

    @Test
    void codedBooleanAndTextValuesEachSurvive() {
        assertThat(build("""
                {"observation_id":"obs-1","subject_cpid":"C","code":"PALMAR_PALLOR",
                 "value_code":"SEVERE","value_code_system":"https://impilo.gov.zw/x"}
                """).getValue()).isInstanceOf(CodeableConcept.class);

        assertThat(build("""
                {"observation_id":"obs-1","subject_cpid":"C","code":"OEDEMA","value_boolean":true}
                """).getValue()).isInstanceOf(BooleanType.class);

        assertThat(build("""
                {"observation_id":"obs-1","subject_cpid":"C","code":"NOTE","value_text":"looks unwell"}
                """).getValue()).isInstanceOf(StringType.class);
    }

    // --- provenance and status ----------------------------------------------------------------------

    @Test
    void theOriginatingObservationIdIsTheBusinessIdentifierSoRepublishingIsIdempotent() {
        Observation obs = build("""
                {"observation_id":"obs-1","subject_cpid":"C","code":"BODY_TEMPERATURE",
                 "value_quantity":38.4,"value_unit":"Cel"}
                """);

        assertThat(obs.getIdentifierFirstRep().getSystem())
                .isEqualTo("https://impilo.gov.zw/pct/observation-id");
        assertThat(obs.getIdentifierFirstRep().getValue()).isEqualTo("obs-1");
    }

    @Test
    void anAmendedObservationIsArchivedAsAmendedNotAsAFreshFinalResult() {
        Observation obs = build("""
                {"observation_id":"obs-1","subject_cpid":"C","code":"BODY_TEMPERATURE",
                 "value_quantity":37.0,"value_unit":"Cel","status":"AMENDED"}
                """);

        assertThat(obs.getStatus()).isEqualTo(Observation.ObservationStatus.AMENDED);
    }

    @Test
    void anUnparseableEffectiveTimeIsOmittedRatherThanGuessed() {
        Observation obs = build("""
                {"observation_id":"obs-1","subject_cpid":"C","code":"BODY_TEMPERATURE",
                 "value_quantity":37.0,"value_unit":"Cel","effective_at":"last Tuesday"}
                """);

        assertThat(obs.hasEffective()).isFalse();
    }
}
