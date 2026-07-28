package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ResultActionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The second half of §25's "results are reviewed and actioned".
 *
 * <p>OROS already records that a result was SEEN, and that half is not retested here. What these
 * cover is the half nothing recorded: what the clinician did next. A potassium of 6.8 can be
 * acknowledged, ticked, and change nothing, and until this table the record could not tell that
 * apart from a result that was acted on.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResultActionServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000051");
    private static final String CPID = "cpid-result-1";
    private static final String JOURNEY = "99999999-9999-4999-8999-999999999991";

    @Autowired
    private ResultActionService service;

    @Autowired
    private ProblemRepository problemRepository;

    @BeforeEach
    void setTrustContext() {
        TrustContextHolder.set(new TrustContext(TENANT, "clin-1", "PRACTITIONER", "EMERGENCY",
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
        m.put("result_ref", "oros-result-1");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i].toString(), kv[i + 1]);
        }
        return m;
    }

    private UUID problem() {
        ProblemEntity p = new ProblemEntity();
        p.setProblemId(UUID.randomUUID());
        p.setTenantId(TENANT);
        p.setSubjectCpid(CPID);
        p.setCode("E87.5");
        p.setDisplay("Hyperkalaemia");
        p.setClinicalStatus("ACTIVE");
        p.setCategory("DIAGNOSIS");
        p.setRecordedBy("clin-1");
        return problemRepository.save(p).getProblemId();
    }

    @Test
    void anActionIsRecordedAgainstTheOrosResultWithoutCopyingIt() {
        // The result stays in OROS. Copying it here would fork the diagnostic record.
        ResultActionEntity e = service.record(body("action", "TREATMENT_STARTED"));

        assertThat(e.getResultRef()).isEqualTo("oros-result-1");
        assertThat(e.getAction()).isEqualTo("TREATMENT_STARTED");
        assertThat(e.getActionedBy()).isEqualTo("clin-1");
    }

    @Test
    void noActionNeededMustJustifyItself() {
        // The assertion this table exists for. NO_ACTION_NEEDED is the reassuring answer, so it is
        // the one an audit later reads as "reviewed and safely closed" — every other action names
        // itself, this one has to say why.
        assertThatThrownBy(() -> service.record(body("action", "NO_ACTION_NEEDED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justify itself");
    }

    @Test
    void noActionNeededWithAReasonIsAccepted() {
        // The safe answer must stay reachable, or clinicians will pick a wrong action to get past
        // the form — which is worse than the blank it replaced.
        ResultActionEntity e = service.record(body(
                "action", "NO_ACTION_NEEDED",
                "rationale", "Haemolysed sample; repeat already back and normal"));

        assertThat(e.getAction()).isEqualTo("NO_ACTION_NEEDED");
        assertThat(e.getRationale()).contains("Haemolysed");
    }

    @Test
    void everyOtherActionStandsWithoutARationale() {
        for (String action : List.of("TREATMENT_STARTED", "REPEAT_REQUESTED", "REFERRED", "ESCALATED")) {
            Map<String, Object> b = body("action", action);
            b.put("result_ref", "oros-result-" + action);
            assertThat(service.record(b).getAction()).isEqualTo(action);
        }
    }

    @Test
    void claimingAResultAddedAProblemMustNameWhichProblem() {
        // Otherwise the claim cannot be followed back to the problem list, and a diagnosis the result
        // established has no visible origin.
        assertThatThrownBy(() -> service.record(body("action", "PROBLEM_ADDED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must name which problem");
    }

    @Test
    void aNamedProblemMustBelongToThisPatient() {
        ProblemEntity other = new ProblemEntity();
        other.setProblemId(UUID.randomUUID());
        other.setTenantId(TENANT);
        other.setSubjectCpid("cpid-someone-else");
        other.setCode("E87.5");
        other.setDisplay("Hyperkalaemia");
        other.setClinicalStatus("ACTIVE");
        other.setCategory("DIAGNOSIS");
        other.setRecordedBy("clin-1");
        problemRepository.save(other);

        assertThatThrownBy(() -> service.record(
                body("action", "PROBLEM_ADDED", "problem_id", other.getProblemId().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to patient");
    }

    @Test
    void aProblemLinkedActionIsAccepted() {
        UUID problemId = problem();
        ResultActionEntity e = service.record(
                body("action", "PROBLEM_ADDED", "problem_id", problemId.toString()));
        assertThat(e.getProblemId()).isEqualTo(problemId);
    }

    @Test
    void anInventedActionIsRefused() {
        assertThatThrownBy(() -> service.record(body("action", "HAD_A_LOOK")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnanchoredActionIsRefusedNamingTheDoctrine() {
        Map<String, Object> b = body("action", "TREATMENT_STARTED");
        b.remove("journey_id");
        assertThatThrownBy(() -> service.record(b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CC-5");
    }

    // ── the read §25 needs ───────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void theStatusReadNamesTheRESULTSTHATHAVENOACTION() {
        service.record(body("action", "TREATMENT_STARTED"));

        Map<String, Object> status = service.actionStatus(
                List.of("oros-result-1", "oros-result-2", "oros-result-3"));

        assertThat((List<Map<String, Object>>) status.get("actioned")).hasSize(1);
        assertThat((List<String>) status.get("not_actioned"))
                .containsExactlyInAnyOrder("oros-result-2", "oros-result-3");
        assertThat((String) status.get("scope_note")).contains("Acknowledged is not actioned");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEmptyResultSetAnswersAboutNothingAndSaysSo() {
        // The dangerous reading: an empty answer taken as "everything has been actioned". PCT does
        // not hold the result list, so it cannot make that claim and must not appear to.
        Map<String, Object> status = service.actionStatus(List.of());

        assertThat((List<String>) status.get("not_actioned")).isEmpty();
        assertThat((String) status.get("scope_note"))
                .contains("answers about nothing")
                .contains("not a statement that every result has been actioned");
    }

    @Test
    void anotherTenantsActionNeverCountsAsThisOnesCoverage() {
        // A leaked action would make a result look actioned when this tenant never touched it.
        service.record(body("action", "TREATMENT_STARTED"));
        TrustContextHolder.set(new TrustContext(
                UUID.fromString("00000000-0000-4000-8000-000000000052"),
                "clin-2", "PRACTITIONER", "EMERGENCY", null, UUID.randomUUID(),
                null, null, null, AccessMode.INTERNAL));

        Map<String, Object> status = service.actionStatus(List.of("oros-result-1"));
        assertThat((List<String>) status.get("not_actioned")).containsExactly("oros-result-1");
    }
}
