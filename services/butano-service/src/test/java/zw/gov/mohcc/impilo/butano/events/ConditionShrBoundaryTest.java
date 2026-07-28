package zw.gov.mohcc.impilo.butano.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Condition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The problem list's boundary into the shared health record.
 *
 * <p>Companion to {@link ObservationShrBoundaryTest}. The same standing rule applies — identifying
 * data stays in VITO, the SHR holds a CPID — and the same reasoning: a rule enforced only by the
 * producer holds until someone changes the producer. These tests exercise the one method that
 * decides what crosses.</p>
 *
 * <p>The status mappings are tested hardest, because the failure they prevent is silent and
 * clinical: a diagnosis whose certainty was never stated must not arrive in the SHR looking
 * confirmed.</p>
 */
class ConditionShrBoundaryTest {

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

    private static Condition build(String json) {
        return ButanoEventConsumer.buildCondition(TENANT_TAG, TENANT, "42", "prob-1", "B20", payload(json));
    }

    @Test
    void theCodedValueAndSubjectCross() {
        Condition c = build("{\"codeSystem\":\"ICD-10\",\"display\":\"HIV disease\"}");
        assertThat(c.getCode().getCodingFirstRep().getCode()).isEqualTo("B20");
        assertThat(c.getCode().getCodingFirstRep().getSystem()).isEqualTo("ICD-10");
        assertThat(c.getSubject().getReference()).isEqualTo("Patient/42");
        assertThat(c.getIdentifierFirstRep().getValue()).isEqualTo("prob-1");
    }

    @Test
    void freeTextNotesCanNeverCrossEvenIfAProducerStartsSendingThem() {
        // notes is the one field on a PCT problem that can hold PII. It is not on the event, and
        // nothing here reads it — so a future producer that started sending it still could not
        // write it into the SHR. Enforced by construction, not trusted of the caller.
        Condition c = build("{\"codeSystem\":\"ICD-10\",\"display\":\"HIV disease\","
                + "\"notes\":\"Patient Jane Doe, 0772-123456, lives at 12 Samora Machel\"}");

        // Assert on the fields the builder actually populates, the way ObservationShrBoundaryTest
        // does — a HAPI resource cannot be walked by Jackson, and a serialiser that throws would
        // make this test pass for the wrong reason.
        String populated = c.getIdentifierFirstRep().getValue()
                + " " + c.getSubject().getReference()
                + " " + c.getCode().getCodingFirstRep().getCode()
                + " " + c.getCode().getCodingFirstRep().getDisplay();

        assertThat(populated).doesNotContain("Jane Doe", "0772-123456", "Samora Machel");
        // note is the FHIR field free text would land in, and nothing writes it.
        assertThat(c.hasNote()).isFalse();
        // The subject is a reference to a Patient resource, never an inline identity.
        assertThat(c.getSubject().getReference()).isEqualTo("Patient/42");
        assertThat(c.hasContained()).isFalse();
    }

    @Test
    void anUnstatedCertaintyNeverArrivesAsConfirmed() {
        // PCT deliberately allows diagnostic_certainty to be null: "never stated". Promoting that to
        // confirmed on the way to the SHR would manufacture a diagnosis nobody made.
        Condition c = build("{\"codeSystem\":\"ICD-10\"}");
        assertThat(c.hasVerificationStatus()).isFalse();
        assertThat(ButanoEventConsumer.mapVerificationStatus(null)).isNull();
    }

    @Test
    void certaintyMapsToItsOwnVerificationStatusAndSuspectedIsNotConfirmed() {
        assertThat(ButanoEventConsumer.mapVerificationStatus("SUSPECTED")).isEqualTo("unconfirmed");
        assertThat(ButanoEventConsumer.mapVerificationStatus("WORKING")).isEqualTo("provisional");
        assertThat(ButanoEventConsumer.mapVerificationStatus("CONFIRMED")).isEqualTo("confirmed");
        assertThat(ButanoEventConsumer.mapVerificationStatus("REFUTED")).isEqualTo("refuted");
    }

    @Test
    void inactiveAndResolvedStayDistinctBecauseTheyAreDifferentClinicalClaims() {
        // "no longer being treated" and "gone" must not collapse into one another.
        assertThat(ButanoEventConsumer.mapClinicalStatus("INACTIVE")).isEqualTo("inactive");
        assertThat(ButanoEventConsumer.mapClinicalStatus("RESOLVED")).isEqualTo("resolved");
        assertThat(ButanoEventConsumer.mapClinicalStatus("RECURRENCE")).isEqualTo("recurrence");
        assertThat(ButanoEventConsumer.mapClinicalStatus("RELAPSE")).isEqualTo("relapse");
    }

    @Test
    void anUnrecognisedStatusIsLeftUnsetRatherThanGuessed() {
        // A Condition with no clinical status is honest; one with the wrong status is not.
        assertThat(ButanoEventConsumer.mapClinicalStatus("SOMETHING_NEW")).isNull();
        Condition c = build("{\"codeSystem\":\"ICD-10\",\"clinicalStatus\":\"SOMETHING_NEW\"}");
        assertThat(c.hasClinicalStatus()).isFalse();
    }
}
