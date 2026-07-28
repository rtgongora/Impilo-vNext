package zw.gov.mohcc.impilo.butano.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The multimorbidity engine's boundary into the shared health record (brief.md §9 -> §19).
 *
 * <p>Companion to {@link ConditionShrBoundaryTest}. The engine's findings are the only cross-disease
 * safety signal this estate produces — a duplicate anticoagulant, an unsafe combination, a treatment
 * burden the patient has quietly stopped carrying — and they are worth nothing if they are visible
 * only on the screen of the clinician who happened to open the multimorbidity view.</p>
 *
 * <p>Severity is tested hardest here for the same reason certainty is tested hardest on Condition: a
 * value nobody stated must not arrive looking like a value somebody did state.</p>
 */
class DetectedIssueShrBoundaryTest {

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

    private static DetectedIssue build(String json) {
        return ButanoEventConsumer.buildDetectedIssue(TENANT_TAG, TENANT, "42",
                "CPID-9|MM_DUPLICATE_ANTICOAGULANT", "MM_DUPLICATE_ANTICOAGULANT", payload(json));
    }

    // --- the boundary rule ---------------------------------------------------------------------

    @Test
    void theCodedIssueAndSeverityCrossAndAreAttachedToThePatient() {
        DetectedIssue issue = build("{\"severity\":\"HIGH\"}");

        assertThat(issue.getCode().getCodingFirstRep().getCode()).isEqualTo("MM_DUPLICATE_ANTICOAGULANT");
        assertThat(issue.getCode().getCodingFirstRep().getSystem())
                .isEqualTo("https://impilo.gov.zw/clinical/multimorbidity");
        assertThat(issue.getPatient().getReference()).isEqualTo("Patient/42");
        assertThat(issue.getSeverity()).isEqualTo(DetectedIssue.DetectedIssueSeverity.HIGH);
        assertThat(issue.getStatus()).isEqualTo(DetectedIssue.DetectedIssueStatus.FINAL);
    }

    @Test
    void theEnginesGeneratedProseCanNeverCrossEvenIfAProducerStartsSendingIt() {
        // The engine's message, explanation and requiredAction are built by interpolating live data
        // into a template. The repeated-investigations finding names whoever ordered each test
        // ("Ordered by Dr Moyo then Dr Ncube"); the visit-burden finding names the clinics; a future
        // template change could name the patient. None of it is on the event and nothing here reads
        // it, so it cannot cross. Enforced by construction, not trusted of the caller.
        DetectedIssue issue = build("""
                {
                  "severity": "HIGH",
                  "message": "Two or more anticoagulants are active: warfarin, rivaroxaban.",
                  "explanation": "Ordered by Dr Moyo then Dr Ncube at Parirenyatwa Ward B4.",
                  "requiredAction": "Phone Tendai Moyo on 0772-123456 before the next dose.",
                  "detail": "Patient Jane Doe, 12 Samora Machel Ave"
                }""");

        // Assert on the fields the builder actually populates, the way the sibling boundary tests do.
        String populated = issue.getIdentifierFirstRep().getValue()
                + " " + issue.getPatient().getReference()
                + " " + issue.getCode().getCodingFirstRep().getCode()
                + " " + issue.getCode().getCodingFirstRep().getDisplay();

        assertThat(populated).doesNotContain("Dr Moyo", "Dr Ncube", "Jane Doe", "0772-123456",
                "Samora Machel", "warfarin", "rivaroxaban", "Parirenyatwa");
        // detail is the FHIR field free text would land in, and nothing writes it.
        assertThat(issue.hasDetail()).isFalse();
        // No display on the coding either: the engine has no fixed label, only the interpolated
        // message, so a display would be the prose smuggled back in through the label.
        assertThat(issue.getCode().getCodingFirstRep().hasDisplay()).isFalse();
        assertThat(issue.hasContained()).isFalse();
    }

    // --- never guess ---------------------------------------------------------------------------

    @Test
    void anUnstatedSeverityIsLeftUnsetRatherThanDefaultedToModerate() {
        // "Moderate" is the value a reader skims past. A severity nobody stated arriving as moderate
        // would quietly downgrade an issue the engine may have rated high, and an unset severity at
        // least makes the reader look at the code.
        assertThat(ButanoEventConsumer.mapDetectedIssueSeverity(null)).isNull();
        assertThat(build("{}").hasSeverity()).isFalse();
    }

    @Test
    void anUnrecognisedSeverityIsLeftUnsetRatherThanGuessed() {
        // The same rule the Condition producer applies to an unrecognised clinical status: a
        // DetectedIssue with no severity is honest; one with the wrong severity is not.
        assertThat(ButanoEventConsumer.mapDetectedIssueSeverity("CATASTROPHIC")).isNull();
        assertThat(build("{\"severity\":\"CATASTROPHIC\"}").hasSeverity()).isFalse();
    }

    @Test
    void eachEngineSeverityKeepsItsOwnLevelAndHighIsNotFlattened() {
        assertThat(ButanoEventConsumer.mapDetectedIssueSeverity("HIGH")).isEqualTo("high");
        assertThat(ButanoEventConsumer.mapDetectedIssueSeverity("MODERATE")).isEqualTo("moderate");
        assertThat(ButanoEventConsumer.mapDetectedIssueSeverity("LOW")).isEqualTo("low");
    }

    // --- idempotency ---------------------------------------------------------------------------

    @Test
    void theIssueKeyIsScopedToTheSubjectSoOnePatientsIssueCannotOverwriteAnothers() {
        // The finding code alone is not unique across patients: every patient on two anticoagulants
        // produces MM_DUPLICATE_ANTICOAGULANT. Keyed on the code alone, the second patient's issue
        // would update the first patient's resource — and the first patient's issue would silently
        // move onto the wrong record.
        assertThat(ButanoEventConsumer.detectedIssueKey("CPID-9", "MM_DUPLICATE_ANTICOAGULANT"))
                .isEqualTo("CPID-9|MM_DUPLICATE_ANTICOAGULANT")
                .isNotEqualTo(ButanoEventConsumer.detectedIssueKey("CPID-8", "MM_DUPLICATE_ANTICOAGULANT"));
    }

    @Test
    void aReplayedAssessmentProducesTheSameBusinessKey() {
        // The multimorbidity view is recomputed every time a clinician opens the screen. Without a
        // stable key that is one new DetectedIssue per page load, and the SHR would report a patient
        // with dozens of duplicate-anticoagulant issues instead of one.
        DetectedIssue first = build("{\"severity\":\"HIGH\"}");
        DetectedIssue replay = build("{\"severity\":\"HIGH\"}");

        assertThat(first.getIdentifierFirstRep().getSystem())
                .isEqualTo("https://impilo.gov.zw/clinical/multimorbidity-issue");
        assertThat(replay.getIdentifierFirstRep().getValue())
                .isEqualTo(first.getIdentifierFirstRep().getValue());
    }

    @Test
    void theTenantTagIsCarriedSoTheIdempotencyLookupCannotCrossTenants() {
        DetectedIssue issue = build("{\"severity\":\"HIGH\"}");
        assertThat(issue.getMeta().getTag()).anySatisfy(tag -> {
            assertThat(tag.getSystem()).isEqualTo(TENANT_TAG);
            assertThat(tag.getCode()).isEqualTo(TENANT.toString());
        });
    }
}
