package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The problem list is the record the whole Adult Medicine pack composes over, so what it does with
 * a field nobody filled in matters as much as what it does with one they did.
 */
@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClinicalAccessGuard accessGuard;

    private ProblemService service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProblemService(problemRepository, outboxRepository, new ObjectMapper(), accessGuard);
        // Stand in for @PrePersist, which JPA fires on save and a mocked repository does not.
        when(problemRepository.save(any())).thenAnswer(inv -> {
            ProblemEntity saved = inv.getArgument(0);
            if (saved.getProblemId() == null) saved.setProblemId(UUID.randomUUID());
            return saved;
        });
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "clinician-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL);
    }

    private ProblemEntity add(Map<String, Object> body) {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            return service.add(body);
        }
    }

    private static Map<String, Object> problem() {
        Map<String, Object> body = new HashMap<>();
        body.put("subject_cpid", "CPID-9");
        body.put("display", "Hypertension");
        body.put("code", "I10");
        body.put("code_system", "ICD-10");
        return body;
    }

    @Test
    void recordsSeverityAgainstTheProblem() {
        Map<String, Object> body = problem();
        body.put("severity", "moderate");
        ProblemEntity p = add(body);
        assertThat(p.getSeverity()).isEqualTo("MODERATE");
        assertThat(p.getDisplay()).isEqualTo("Hypertension");
        assertThat(p.getCode()).isEqualTo("I10");
    }

    /**
     * The clinical point of the column. An unstated severity has to stay unstated: defaulting it
     * would write down an assessment nobody made, and of the available defaults "mild" is the one
     * that would stop the next reader looking any further.
     */
    @Test
    void leavesAnUnstatedSeverityUnstated() {
        assertThat(add(problem()).getSeverity()).isNull();
        Map<String, Object> blank = problem();
        blank.put("severity", "   ");
        assertThat(add(blank).getSeverity()).isNull();
    }

    /**
     * The author is the authenticated actor, never a value the caller supplied. A forgeable author
     * would let a diagnosis be attributed to a clinician who never made it.
     */
    @Test
    void stampsTheAuthorFromTheTrustContextAndIgnoresTheBody() {
        Map<String, Object> body = problem();
        body.put("recorded_by", "someone-else");
        assertThat(add(body).getRecordedBy()).isEqualTo("clinician-1");
    }

    @Test
    void defaultsStatusToActiveAndCategoryToDiagnosis() {
        ProblemEntity p = add(problem());
        assertThat(p.getClinicalStatus()).isEqualTo("ACTIVE");
        assertThat(p.getCategory()).isEqualTo("DIAGNOSIS");
    }
}
