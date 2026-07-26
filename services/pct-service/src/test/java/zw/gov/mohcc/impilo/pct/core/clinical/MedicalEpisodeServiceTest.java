package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicalEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicalEpisodeProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicalEpisodeProblemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicalEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

/**
 * The medical episode is the object adult medicine is organised around — a course of care with a
 * responsible service, a start, an end and the problems it exists to manage.
 */
@ExtendWith(MockitoExtension.class)
class MedicalEpisodeServiceTest {

    @Mock private MedicalEpisodeRepository episodeRepository;
    @Mock private MedicalEpisodeProblemRepository episodeProblemRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClinicalAccessGuard accessGuard;

    private MedicalEpisodeService service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MedicalEpisodeService(episodeRepository, episodeProblemRepository,
                problemRepository, outboxRepository, new ObjectMapper(), accessGuard);
        lenient().when(episodeRepository.save(any())).thenAnswer(inv -> {
            MedicalEpisodeEntity saved = inv.getArgument(0);
            if (saved.getEpisodeId() == null) saved.setEpisodeId(UUID.randomUUID());
            return saved;
        });
        lenient().when(episodeProblemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "clinician-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL);
    }

    private <T> T asClinician(java.util.function.Supplier<T> action) {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            return action.get();
        }
    }

    private static Map<String, Object> episode() {
        Map<String, Object> body = new HashMap<>();
        body.put("subject_cpid", "CPID-9");
        body.put("episode_type", "CHRONIC_DISEASE");
        body.put("title", "Hypertension management");
        return body;
    }

    private MedicalEpisodeEntity open(Map<String, Object> body) {
        return asClinician(() -> service.open(body));
    }

    // ── Opening ─────────────────────────────────────────────────────────────────

    @Test
    void opensAnEpisodeWithTodayAsTheDefaultStart() {
        MedicalEpisodeEntity e = open(episode());
        assertThat(e.getEpisodeType()).isEqualTo("CHRONIC_DISEASE");
        assertThat(e.getStatus()).isEqualTo("ACTIVE");
        assertThat(e.getStartedOn()).isEqualTo(LocalDate.now());
        assertThat(e.getCreatedBy()).isEqualTo("clinician-1");
        assertThat(e.getEndedOn()).isNull();
    }

    /**
     * A dialysis pathway agreed in clinic but not yet begun is a real state. Recording it as ACTIVE
     * would overstate what is actually happening to the patient.
     */
    @Test
    void supportsAPlannedCourseOfCareThatHasNotStarted() {
        Map<String, Object> body = episode();
        body.put("status", "PLANNED");
        body.put("episode_type", "DIAGNOSTIC_WORKUP");
        assertThat(open(body).getStatus()).isEqualTo("PLANNED");
    }

    /** The seam to emergency care — the link that makes an admission from ED traceable. */
    @Test
    void carriesTheEmergencyEpisodeItBeganWith() {
        UUID emergencyEpisodeId = UUID.randomUUID();
        Map<String, Object> body = episode();
        body.put("episode_type", "ACUTE_ILLNESS");
        body.put("emergency_episode_id", emergencyEpisodeId.toString());
        assertThat(open(body).getEmergencyEpisodeId()).isEqualTo(emergencyEpisodeId);
    }

    @Test
    void leavesTheEmergencyLinkNullForAnEpisodeThatDidNotBeginInEmergencyCare() {
        assertThat(open(episode()).getEmergencyEpisodeId()).isNull();
    }

    /** Opening straight into a closed status would violate the end-date CHECK with a 500. */
    @Test
    void refusesToOpenAnEpisodeThatIsAlreadyClosed() {
        Map<String, Object> body = episode();
        body.put("status", "FINISHED");
        assertThatThrownBy(() -> open(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be opened");
    }

    @Test
    void refusesATypeOutsideTheVocabulary() {
        Map<String, Object> body = episode();
        body.put("episode_type", "GENERAL_CARE");
        assertThatThrownBy(() -> open(body)).isInstanceOf(IllegalArgumentException.class);
    }

    // ── Problems ────────────────────────────────────────────────────────────────

    @Test
    void attachesAnExistingProblemToTheEpisode() {
        MedicalEpisodeEntity e = open(episode());
        ProblemEntity p = problemFor("CPID-9");
        stub(e, p);

        MedicalEpisodeProblemEntity link = asClinician(() ->
                service.attachProblem(e.getEpisodeId(), p.getProblemId(), "PRIMARY"));

        assertThat(link.getId().getEpisodeId()).isEqualTo(e.getEpisodeId());
        assertThat(link.getId().getProblemId()).isEqualTo(p.getProblemId());
        assertThat(link.getRole()).isEqualTo("PRIMARY");
    }

    /**
     * A heart-failure admission that also manages the patient's diabetes is not a diabetes episode.
     */
    @Test
    void distinguishesTheReasonForTheEpisodeFromWhatIsManagedAlongside() {
        MedicalEpisodeEntity e = open(episode());
        ProblemEntity p = problemFor("CPID-9");
        stub(e, p);

        assertThat(asClinician(() ->
                service.attachProblem(e.getEpisodeId(), p.getProblemId(), "SECONDARY")).getRole())
                .isEqualTo("SECONDARY");
    }

    /** Attaching another patient's problem would attribute their diagnosis to this course of care. */
    @Test
    void refusesAProblemBelongingToADifferentPatient() {
        MedicalEpisodeEntity e = open(episode());
        ProblemEntity other = problemFor("CPID-OTHER");
        stub(e, other);

        assertThatThrownBy(() -> asClinician(() ->
                service.attachProblem(e.getEpisodeId(), other.getProblemId(), "PRIMARY")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different patient");
    }

    // ── Closing ─────────────────────────────────────────────────────────────────

    /**
     * The reason this is required rather than defaulted. COMPLETED, DIED, TRANSFERRED and
     * LOST_TO_FOLLOW_UP all leave the status FINISHED; defaulting to COMPLETED would record a good
     * outcome for a patient who was lost, and every outcome indicator built on this table would
     * inherit that.
     */
    @Test
    void refusesToCloseAnEpisodeWithoutSayingWhyItEnded() {
        MedicalEpisodeEntity e = open(episode());
        stub(e, null);
        assertThatThrownBy(() -> asClinician(() ->
                service.close(e.getEpisodeId(), "FINISHED", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end_reason");
    }

    @Test
    void closesWithAReasonAndAnEndDate() {
        MedicalEpisodeEntity e = open(episode());
        stub(e, null);

        MedicalEpisodeEntity closed = asClinician(() ->
                service.close(e.getEpisodeId(), "FINISHED", "LOST_TO_FOLLOW_UP", null));

        assertThat(closed.getStatus()).isEqualTo("FINISHED");
        assertThat(closed.getEndReason()).isEqualTo("LOST_TO_FOLLOW_UP");
        assertThat(closed.getEndedOn()).isEqualTo(LocalDate.now());
    }

    @Test
    void refusesAnEndDateBeforeTheStart() {
        Map<String, Object> body = episode();
        body.put("started_on", LocalDate.now().toString());
        MedicalEpisodeEntity e = open(body);
        stub(e, null);

        assertThatThrownBy(() -> asClinician(() -> service.close(
                e.getEpisodeId(), "FINISHED", "COMPLETED", LocalDate.now().minusDays(5).toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before it started");
    }

    @Test
    void refusesToCloseIntoAnOpenStatus() {
        MedicalEpisodeEntity e = open(episode());
        stub(e, null);
        assertThatThrownBy(() -> asClinician(() ->
                service.close(e.getEpisodeId(), "ACTIVE", "COMPLETED", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A reopened episode must not still carry the end it no longer has. */
    @Test
    void reopeningClearsTheEndSoTheRecordDoesNotClaimTwoTruths() {
        MedicalEpisodeEntity e = open(episode());
        stub(e, null);
        asClinician(() -> service.close(e.getEpisodeId(), "FINISHED", "COMPLETED", null));

        MedicalEpisodeEntity reopened = asClinician(() -> service.reopen(e.getEpisodeId()));

        assertThat(reopened.getStatus()).isEqualTo("ACTIVE");
        assertThat(reopened.getEndedOn()).isNull();
        assertThat(reopened.getEndReason()).isNull();
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────

    private ProblemEntity problemFor(String subjectCpid) {
        ProblemEntity p = new ProblemEntity();
        p.setProblemId(UUID.randomUUID());
        p.setTenantId(TENANT_ID);
        p.setSubjectCpid(subjectCpid);
        p.setDisplay("Hypertension");
        p.setCategory("DIAGNOSIS");
        p.setClinicalStatus("ACTIVE");
        p.setRecordedBy("clinician-1");
        return p;
    }

    private void stub(MedicalEpisodeEntity e, ProblemEntity p) {
        lenient().when(episodeRepository.findById(e.getEpisodeId())).thenReturn(Optional.of(e));
        if (p != null) {
            lenient().when(problemRepository.findById(p.getProblemId())).thenReturn(Optional.of(p));
        }
    }
}
