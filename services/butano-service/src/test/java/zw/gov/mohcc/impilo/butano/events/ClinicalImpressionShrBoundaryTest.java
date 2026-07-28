package zw.gov.mohcc.impilo.butano.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.ClinicalImpression;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The structured examination's boundary into the shared health record (brief.md §19).
 *
 * <p>Companion to {@link ConditionShrBoundaryTest} and {@link ObservationShrBoundaryTest}, and the
 * consumer half of pct-service's {@code ExaminationShrBoundaryTest}. The same standing rule applies —
 * identifying data stays in VITO and the SHR holds a CPID — and the same reasoning: a rule enforced
 * only by the producer holds until someone changes the producer.</p>
 *
 * <p>The state mapping is tested hardest, because the failure it prevents is silent and clinical.
 * PCT's examination framework has six states rather than two precisely so that "nobody looked" cannot
 * be read as "nothing wrong"; if this boundary folded NOT_EXAMINED, UNABLE_TO_EXAMINE or DEFERRED
 * into the code used for NORMAL, the clinician at the next facility — who was not in the room and has
 * only this record — would read a normal abdomen for an abdomen nobody has ever palpated, and would
 * not examine it.</p>
 */
class ClinicalImpressionShrBoundaryTest {

    private static final String TENANT_TAG = "https://impilo.gov.zw/tenant";
    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String STATE_SYSTEM = "https://impilo.gov.zw/pct/examination-state";
    private static final String REGION_SYSTEM = "https://impilo.gov.zw/pct/examination-region";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode payload(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ClinicalImpression build(String json) {
        return ButanoEventConsumer.buildClinicalImpression(
                TENANT_TAG, TENANT, "42", "exam-1", payload(json));
    }

    /** The code carried under a given system on the nth finding, or null. */
    private static String codeUnder(ClinicalImpression impression, int index, String system) {
        for (Coding coding : impression.getFinding().get(index).getItemCodeableConcept().getCoding()) {
            if (system.equals(coding.getSystem())) {
                return coding.getCode();
            }
        }
        return null;
    }

    // --- the boundary rule ---------------------------------------------------------------------

    @Test
    void theRegionStateAndCodedFindingCrossAndAreAttachedToTheSubject() {
        ClinicalImpression c = build("""
                {"findings":[{"region":"RESPIRATORY","state":"ABNORMAL","findingCode":"R09.8"}]}""");

        assertThat(c.getSubject().getReference()).isEqualTo("Patient/42");
        assertThat(c.getIdentifierFirstRep().getValue()).isEqualTo("exam-1");
        assertThat(codeUnder(c, 0, REGION_SYSTEM)).isEqualTo("RESPIRATORY");
        assertThat(codeUnder(c, 0, STATE_SYSTEM)).isEqualTo("abnormal");
        assertThat(codeUnder(c, 0, "https://impilo.gov.zw/pct/examination-finding")).isEqualTo("R09.8");
        assertThat(c.getStatus()).isEqualTo(ClinicalImpression.ClinicalImpressionStatus.COMPLETED);
    }

    @Test
    void freeTextExaminationProseCanNeverCrossEvenIfAProducerStartsSendingIt() {
        // detail, context_note and site are the fields a clinician types prose into on an examination,
        // and prose is the one shape that carries a name, a phone number or an address. They are not
        // on the event and nothing here reads them, so a future producer that started sending them
        // still could not write them into the SHR. Enforced by construction, not trusted of the caller.
        ClinicalImpression c = build("""
                {
                  "context_note": "Brought by her sister Rudo Moyo of 12 Samora Machel Ave",
                  "findings": [{
                    "region": "RESPIRATORY",
                    "state": "ABNORMAL",
                    "detail": "crackles; husband Tendai Moyo, 0772-123456",
                    "site": "left base",
                    "note": "call the daughter on 0712-987654"
                  }]
                }""");

        // Assert on the fields the builder actually populates, the way the sibling boundary tests do:
        // a HAPI resource cannot be walked by Jackson, and a serialiser that threw would make this
        // test pass for the wrong reason.
        StringBuilder populated = new StringBuilder()
                .append(c.getIdentifierFirstRep().getValue())
                .append(' ').append(c.getSubject().getReference())
                .append(' ').append(c.getSummary());
        for (Coding coding : c.getFinding().get(0).getItemCodeableConcept().getCoding()) {
            populated.append(' ').append(coding.getSystem()).append(' ').append(coding.getCode());
        }

        assertThat(populated.toString())
                .doesNotContain("Tendai Moyo", "Rudo Moyo", "0772-123456", "0712-987654",
                        "Samora Machel", "left base");
        // basis and note are the FHIR fields prose would land in, and nothing writes them.
        assertThat(c.getFinding().get(0).hasBasis()).isFalse();
        assertThat(c.hasNote()).isFalse();
        // The subject is a reference to a Patient resource, never an inline identity.
        assertThat(c.hasContained()).isFalse();
    }

    // --- NOT_EXAMINED is not NORMAL ------------------------------------------------------------

    @Test
    void theThreeNotExaminedStatesAreEachDistinctFromNormalAndFromEachOther() {
        // The single property the examination framework exists to hold, asserted at the boundary where
        // it would be cheapest to lose. "Nobody looked", "somebody tried and could not" and "somebody
        // chose to look later" are three different clinical facts and none of them is "normal": the
        // first says examine it, the second says what has to change first, the third says when.
        assertThat(ButanoEventConsumer.mapExaminationState("NORMAL")).isEqualTo("normal");
        assertThat(ButanoEventConsumer.mapExaminationState("NOT_EXAMINED")).isEqualTo("not-examined");
        assertThat(ButanoEventConsumer.mapExaminationState("UNABLE_TO_EXAMINE")).isEqualTo("unable-to-examine");
        assertThat(ButanoEventConsumer.mapExaminationState("DEFERRED")).isEqualTo("deferred");
        assertThat(ButanoEventConsumer.mapExaminationState("ABNORMAL")).isEqualTo("abnormal");
        assertThat(ButanoEventConsumer.mapExaminationState("UNCERTAIN")).isEqualTo("uncertain");

        // Six inputs, six distinct outputs. This is the assertion that goes red if anyone ever
        // "simplifies" the mapping by folding the unexamined states together or onto normal.
        assertThat(List.of(
                ButanoEventConsumer.mapExaminationState("NORMAL"),
                ButanoEventConsumer.mapExaminationState("ABNORMAL"),
                ButanoEventConsumer.mapExaminationState("UNCERTAIN"),
                ButanoEventConsumer.mapExaminationState("NOT_EXAMINED"),
                ButanoEventConsumer.mapExaminationState("UNABLE_TO_EXAMINE"),
                ButanoEventConsumer.mapExaminationState("DEFERRED")))
                .doesNotHaveDuplicates();

        // And in the built resource, not only in the mapper.
        ClinicalImpression c = build("""
                {"findings":[
                  {"region":"ABDOMEN","state":"NORMAL"},
                  {"region":"NEUROLOGY","state":"NOT_EXAMINED"},
                  {"region":"SKIN","state":"UNABLE_TO_EXAMINE"},
                  {"region":"FEET","state":"DEFERRED"}]}""");
        assertThat(List.of(
                codeUnder(c, 0, STATE_SYSTEM), codeUnder(c, 1, STATE_SYSTEM),
                codeUnder(c, 2, STATE_SYSTEM), codeUnder(c, 3, STATE_SYSTEM)))
                .containsExactly("normal", "not-examined", "unable-to-examine", "deferred");
    }

    @Test
    void onlyTheThreeExaminedStatesCountAsHavingBeenExamined() {
        // Mirrors PCT's EXAMINED_STATES. UNCERTAIN counts because the examiner did look and said so;
        // the other three do not, and the summary's counts depend on this being right.
        assertThat(ButanoEventConsumer.isExaminedState("normal")).isTrue();
        assertThat(ButanoEventConsumer.isExaminedState("abnormal")).isTrue();
        assertThat(ButanoEventConsumer.isExaminedState("uncertain")).isTrue();
        assertThat(ButanoEventConsumer.isExaminedState("not-examined")).isFalse();
        assertThat(ButanoEventConsumer.isExaminedState("unable-to-examine")).isFalse();
        assertThat(ButanoEventConsumer.isExaminedState("deferred")).isFalse();
    }

    @Test
    void theSummaryCountsTheUnexaminedRegionsSeparatelyAndSaysTheyAreNotNormal() {
        // The read-side half of §7 carried into the SHR: a reader scanning findings sees what was
        // looked at, never what was not, because absence has no row.
        ClinicalImpression c = build("""
                {"findings":[
                  {"region":"ABDOMEN","state":"NORMAL"},
                  {"region":"RESPIRATORY","state":"ABNORMAL"},
                  {"region":"NEUROLOGY","state":"NOT_EXAMINED"},
                  {"region":"SKIN","state":"DEFERRED"}]}""");

        assertThat(c.getSummary())
                .contains("4 region(s) recorded")
                .contains("2 examined")
                .contains("2 recorded as not examined")
                .contains("not a normal region");
    }

    // --- never guess ---------------------------------------------------------------------------

    @Test
    void anUnrecognisedStateIsMarkedAbsentRatherThanRenderedAsNormal() {
        // The same rule the Condition producer applies to an unstated diagnostic certainty. A state
        // this record cannot interpret must not silently become the reassuring one — the reader
        // cannot tell a guess from a fact, so it is marked explicitly unknown instead.
        assertThat(ButanoEventConsumer.mapExaminationState("PROBABLY_FINE")).isNull();
        assertThat(ButanoEventConsumer.mapExaminationState(null)).isNull();

        ClinicalImpression c = build("""
                {"findings":[{"region":"ABDOMEN","state":"PROBABLY_FINE"}]}""");
        assertThat(codeUnder(c, 0, STATE_SYSTEM)).isNull();
        assertThat(codeUnder(c, 0, "http://terminology.hl7.org/CodeSystem/data-absent-reason"))
                .isEqualTo("unknown");
        // The region is still named: dropping the finding would hide that the region was considered.
        assertThat(codeUnder(c, 0, REGION_SYSTEM)).isEqualTo("ABDOMEN");
        // And it is not counted among the examined regions.
        assertThat(c.getSummary()).contains("0 examined")
                .contains("could not interpret");
    }

    // --- idempotency ---------------------------------------------------------------------------

    @Test
    void theExaminationIdIsTheBusinessKeySoAReplayCannotCreateASecondImpression() {
        // Keyed exactly the way archiveProblem keys a Condition on the PCT problem id. Two
        // ClinicalImpressions for one examination is a record that cannot be counted, and the
        // duplicate would read as a second examination that never happened.
        ClinicalImpression first = build("""
                {"findings":[{"region":"ABDOMEN","state":"NORMAL"}]}""");
        ClinicalImpression replay = build("""
                {"findings":[{"region":"ABDOMEN","state":"NORMAL"}]}""");

        assertThat(first.getIdentifierFirstRep().getSystem())
                .isEqualTo("https://impilo.gov.zw/pct/examination-id");
        assertThat(replay.getIdentifierFirstRep().getValue())
                .isEqualTo(first.getIdentifierFirstRep().getValue());
    }

    @Test
    void theTenantTagIsCarriedSoTheIdempotencyLookupCannotCrossTenants() {
        ClinicalImpression c = build("""
                {"findings":[{"region":"ABDOMEN","state":"NORMAL"}]}""");
        assertThat(c.getMeta().getTag()).anySatisfy(tag -> {
            assertThat(tag.getSystem()).isEqualTo(TENANT_TAG);
            assertThat(tag.getCode()).isEqualTo(TENANT.toString());
        });
    }
}
