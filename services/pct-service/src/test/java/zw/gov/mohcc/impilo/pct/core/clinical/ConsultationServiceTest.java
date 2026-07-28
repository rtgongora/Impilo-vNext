package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ConsultationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MdtDecisionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consultation and MDT (brief.md §14), and the two failures §23 named.
 *
 * <p><strong>The property under test throughout: asking a question never moves the patient.</strong>
 * §23.10 asks for "surgical consultation without loss of ownership". Loss of ownership is rarely a
 * decision anybody makes — it is what happens when nobody records who still holds the patient and
 * both teams assume the other does.</p>
 *
 * <p>Purpose EMERGENCY so {@code ClinicalAccessGuard} waives the care-relationship lookup: the
 * subject here is the consultation logic, and a journey-resolution failure would fail these tests
 * for an unrelated reason. The CC-5 anchor requirement is still asserted directly.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConsultationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000041");
    private static final String CPID = "cpid-consult-1";
    private static final String JOURNEY = "88888888-8888-4888-8888-888888888881";

    @Autowired
    private ConsultationService service;

    @Autowired
    private ProblemRepository problemRepository;

    @BeforeEach
    void setTrustContext() {
        TrustContextHolder.set(new TrustContext(TENANT, "med-1", "PRACTITIONER", "EMERGENCY",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clear() {
        TrustContextHolder.clear();
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subject_cpid", CPID);
        m.put("journey_id", JOURNEY);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i].toString(), kv[i + 1]);
        }
        return m;
    }

    private Map<String, Object> request() {
        return body("requesting_service", "MEDICINE", "asked_of_service", "SURGERY",
                "question", "Is this abdomen surgical?");
    }

    // ── ownership ────────────────────────────────────────────────────────────────

    @Test
    void aConsultationRecordsWhoStillHoldsThePatient() {
        ConsultationEntity e = service.request(request());
        assertThat(e.getOwningService()).isEqualTo("MEDICINE");
    }

    @Test
    void answeringDoesNotMoveOwnership() {
        // The assertion this whole table exists for. Surgery advises; medicine keeps the patient.
        ConsultationEntity e = service.request(request());
        ConsultationEntity answered = service.answer(e.getConsultationId(),
                Map.of("advice", "Not surgical; treat medically"));

        assertThat(answered.getOwningService()).isEqualTo("MEDICINE");
        assertThat(answered.getStatus()).isEqualTo("ANSWERED");
    }

    @Test
    void recommendingATakeoverStillDoesNotPerformOne() {
        // A recommendation that silently transferred care would make the ownership column decorative.
        ConsultationEntity e = service.request(request());
        ConsultationEntity answered = service.answer(e.getConsultationId(),
                Map.of("advice", "Surgical — we should take over", "takeover_recommended", true));

        assertThat(answered.isTakeoverRecommended()).isTrue();
        assertThat(answered.getOwningService()).isEqualTo("MEDICINE");
    }

    @Test
    void decliningDoesNotMoveOwnershipEither() {
        ConsultationEntity e = service.request(request());
        ConsultationEntity declined = service.decline(e.getConsultationId(), "No surgeon on site today");

        assertThat(declined.getOwningService()).isEqualTo("MEDICINE");
        assertThat(declined.getDeclineReason()).contains("No surgeon");
    }

    // ── the shapes that are refused ──────────────────────────────────────────────

    @Test
    void aConsultationWithNoQuestionIsRefusedAsAReferralOfThePatient() {
        Map<String, Object> b = request();
        b.put("question", "   ");
        assertThatThrownBy(() -> service.request(b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referral of the patient");
    }

    @Test
    void answeringWithNoAdviceIsRefused() {
        // An answered consultation with no advice closes the loop in the responder's worklist while
        // leaving the question unanswered in the record — the requester stops waiting for nothing.
        ConsultationEntity e = service.request(request());
        assertThatThrownBy(() -> service.answer(e.getConsultationId(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closes the loop");
    }

    @Test
    void decliningWithNoReasonIsRefused() {
        ConsultationEntity e = service.request(request());
        assertThatThrownBy(() -> service.decline(e.getConsultationId(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ask again");
    }

    @Test
    void aServiceCannotConsultItself() {
        Map<String, Object> b = request();
        b.put("asked_of_service", "MEDICINE");
        assertThatThrownBy(() -> service.request(b)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnanchoredConsultationIsRefusedNamingTheDoctrine() {
        Map<String, Object> b = request();
        b.remove("journey_id");
        assertThatThrownBy(() -> service.request(b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CC-5");
    }

    @Test
    void anAlreadyAnsweredConsultationCannotBeAnsweredAgain() {
        ConsultationEntity e = service.request(request());
        service.answer(e.getConsultationId(), Map.of("advice", "Not surgical"));
        assertThatThrownBy(() -> service.answer(e.getConsultationId(), Map.of("advice", "Actually surgical")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theWorklistShowsOnlyWhatIsStillOpenForThatService() {
        ConsultationEntity open = service.request(request());
        ConsultationEntity closed = service.request(body(
                "requesting_service", "MEDICINE", "asked_of_service", "SURGERY",
                "question", "Second question"));
        service.answer(closed.getConsultationId(), Map.of("advice", "Answered"));

        assertThat(service.worklist("SURGERY")).extracting(ConsultationEntity::getConsultationId)
                .containsExactly(open.getConsultationId());
        assertThat(service.worklist("CARDIOLOGY")).isEmpty();
    }

    // ── MDT ──────────────────────────────────────────────────────────────────────

    private Map<String, Object> mdtBody() {
        return body("meeting_type", "ONCOLOGY",
                "participants", "Oncology, respiratory, radiology, palliative",
                "chaired_by", "Dr M",
                "decision", "Proceed to staging CT then systemic therapy");
    }

    @Test
    void anMdtDecisionCarriesItsChairParticipantsAndDate() {
        MdtDecisionEntity e = service.recordDecision(mdtBody());
        assertThat(e.getChairedBy()).isEqualTo("Dr M");
        assertThat(e.getParticipants()).contains("radiology");
        assertThat(e.getMetOn()).isNotNull();
        assertThat(e.getRecordedBy()).isEqualTo("med-1");
    }

    @Test
    void anUnattributedDecisionIsRefused() {
        // §23.7's failure: a decision that sets treatment intent with no author is not a governed
        // decision, it is a rumour with clinical consequences.
        for (String field : List.of("participants", "chaired_by", "decision")) {
            Map<String, Object> b = mdtBody();
            b.put(field, "   ");
            assertThatThrownBy(() -> service.recordDecision(b), field)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rumour with clinical consequences");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theDecisionLinksTheProblemsItWasAboutRatherThanDescribingThem() {
        ProblemEntity p = new ProblemEntity();
        p.setProblemId(UUID.randomUUID());
        p.setTenantId(TENANT);
        p.setSubjectCpid(CPID);
        p.setCode("C34");
        p.setDisplay("Malignant neoplasm of lung");
        p.setClinicalStatus("ACTIVE");
        p.setCategory("DIAGNOSIS");
        p.setRecordedBy("med-1");
        problemRepository.save(p);

        Map<String, Object> b = mdtBody();
        b.put("problem_ids", List.of(p.getProblemId().toString()));
        MdtDecisionEntity e = service.recordDecision(b);

        List<String> linked = (List<String>) service.decision(e.getDecisionId()).get("problem_ids");
        assertThat(linked).containsExactly(p.getProblemId().toString());
    }

    @Test
    void anotherPatientsProblemCannotBeLinkedToThisMeeting() {
        // Linking another person's diagnosis would attribute one patient's cancer to another's record.
        ProblemEntity other = new ProblemEntity();
        other.setProblemId(UUID.randomUUID());
        other.setTenantId(TENANT);
        other.setSubjectCpid("cpid-someone-else");
        other.setCode("C34");
        other.setDisplay("Malignant neoplasm of lung");
        other.setClinicalStatus("ACTIVE");
        other.setCategory("DIAGNOSIS");
        other.setRecordedBy("med-1");
        problemRepository.save(other);

        Map<String, Object> b = mdtBody();
        b.put("problem_ids", List.of(other.getProblemId().toString()));
        assertThatThrownBy(() -> service.recordDecision(b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to patient");
    }

    @Test
    void anUnrecordedTreatmentIntentIsNullAndSaysWhatThatMeans() {
        // "The meeting did not set an intent" and "an intent was set and not recorded" are different,
        // and NOT_SET is a value a meeting can deliberately choose. Defaulting would erase both.
        MdtDecisionEntity e = service.recordDecision(mdtBody());
        Map<String, Object> read = service.decision(e.getDecisionId());

        assertThat(read.get("treatment_intent")).isNull();
        assertThat((String) read.get("treatment_intent_note")).contains("not the same as an intent of NOT_SET");
    }

    @Test
    void anInventedMeetingTypeOrIntentIsRefused() {
        Map<String, Object> b = mdtBody();
        b.put("meeting_type", "COFFEE_MORNING");
        assertThatThrownBy(() -> service.recordDecision(b)).isInstanceOf(IllegalArgumentException.class);

        Map<String, Object> c = mdtBody();
        c.put("treatment_intent", "PROBABLY_CURATIVE");
        assertThatThrownBy(() -> service.recordDecision(c)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnanchoredDecisionIsRefused() {
        Map<String, Object> b = mdtBody();
        b.remove("journey_id");
        assertThatThrownBy(() -> service.recordDecision(b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CC-5");
    }
}
