package zw.gov.mohcc.impilo.clinical.multimorbidity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.events.ClinicalKafkaTopics;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The multimorbidity engine's boundary into the shared health record (brief.md §9 -> §19).
 *
 * <p>Producer half of butano-service's {@code DetectedIssueShrBoundaryTest}. The rule is enforced at
 * both ends deliberately: a boundary held only by the consumer is one a producer change can flood,
 * and a boundary held only by the producer is one a consumer change can leak.</p>
 *
 * <p>Named {@code *Test} and not {@code *IT}: surefire skips {@code *IT} in this repo, and a boundary
 * test that never runs is worse than none, because it reads as coverage.</p>
 */
class MultimorbidityShrBoundaryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static MultimorbidityView.Finding contaminatedFinding() {
        // Exactly the shape the engine's repeatedInvestigations detector produces, which interpolates
        // the name of whoever ordered each test into the explanation.
        return new MultimorbidityView.Finding(
                "MM_REPEAT_HBA1C",
                "LOW",
                "HBA1C was repeated after 21 day(s); the shortest interval that can show a change is 90.",
                "Ordered by Dr Tendai Moyo then Dr Rudo Ncube at Parirenyatwa Ward B4. Phone the "
                + "patient on 0772-123456 before repeating.",
                "Check whether the earlier result answers the question before repeating.");
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // --- the boundary rule ---------------------------------------------------------------------

    @Test
    void theEnginesGeneratedProseNeverReachesTheWire() {
        // message, explanation and requiredAction are templates filled with live data: medicine names,
        // clinic names, and the names of the clinicians who ordered each test. That is identifying
        // data about a provider, and one template change away from identifying data about the patient.
        // The SHR holds a CPID and coded facts; the prose stays here, where the clinician who opened
        // the view can already read all of it.
        Map<String, Object> payload = MultimorbidityShrPublisher.payload(
                "CPID-9", "tenant-1",
                MultimorbidityView.Detection.detected("repeated_investigations", "Repeated investigations",
                        List.of(contaminatedFinding())),
                contaminatedFinding(), "2026.07", "2026-07-28T09:15:30Z");

        String wire = json(payload);
        assertThat(wire).doesNotContain("Tendai Moyo", "Rudo Ncube", "Parirenyatwa", "0772-123456");
        assertThat(wire).doesNotContain("message", "explanation", "requiredAction", "required_action");
        // What DOES cross: the coded issue, its severity, the detection it came from, and the content
        // version that produced it — every one of them queryable and none of them prose.
        assertThat(payload).containsEntry("code", "MM_REPEAT_HBA1C")
                .containsEntry("severity", "LOW")
                .containsEntry("detectionKey", "repeated_investigations")
                .containsEntry("subjectCpid", "CPID-9")
                .containsEntry("contentVersion", "2026.07");
    }

    // --- unanswered is not clear -----------------------------------------------------------------

    @Test
    void onlyDetectedFindingsAreFiledAndAnUndeterminedCheckIsNeverFiledAsAClearOne() {
        // The reassurance rule, carried to the SHR. A detection that could not be answered must not
        // produce a resource — and, just as importantly, must not produce a "nothing found" resource,
        // because a later reader would take that as a completed check. NOT_DETECTED likewise: the
        // engine's own view says which checks were answered; the SHR carries the positive findings.
        MultimorbidityView view = new MultimorbidityView(
                List.of(),
                List.of(
                        MultimorbidityView.Detection.detected("unsafe_combinations", "Unsafe combinations",
                                List.of(new MultimorbidityView.Finding("MM_UNSAFE_X", "HIGH", "m", "e", "a"))),
                        MultimorbidityView.Detection.undetermined("duplicate_medicines", "Duplicate medicines",
                                "The medicine list could not be obtained."),
                        MultimorbidityView.Detection.clear("competing_dietary_advice", "Competing dietary advice")),
                "2026.07", "APPROVED", "This view is partial.");

        assertThat(MultimorbidityShrPublisher.detectedCodes(view)).containsExactly("MM_UNSAFE_X");
    }

    // --- routing -----------------------------------------------------------------------------------

    @Test
    void theEventTypeIsRoutedToItsOwnTopicAndNotTheCatchAll() {
        // An event type absent from the topic map falls to impilo.clinical.events, which BUTANO does
        // not subscribe to — so the issue would publish successfully and reach nobody, which is the
        // exact failure mode this estate has already shipped more than once.
        assertThat(ClinicalKafkaTopics.topicForEventType(MultimorbidityShrPublisher.EVENT_TYPE))
                .isEqualTo("impilo.clinical.multimorbidity.issue.detected")
                .isNotEqualTo("impilo.clinical.events");
    }
}
