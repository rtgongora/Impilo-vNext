package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProblemLinkEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemLinkRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ProblemRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

/**
 * What makes a problem list clinically usable is not the list — it is knowing what a diagnosis
 * rests on, what it caused, what is treating it and what is watching it.
 */
@ExtendWith(MockitoExtension.class)
class ProblemLinkServiceTest {

    @Mock private ProblemLinkRepository linkRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private ClinicalAccessGuard accessGuard;

    private ProblemLinkService service;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProblemLinkService(linkRepository, problemRepository, accessGuard);
        lenient().when(linkRepository.save(any())).thenAnswer(inv -> {
            ProblemLinkEntity l = inv.getArgument(0);
            if (l.getLinkId() == null) l.setLinkId(UUID.randomUUID());
            return l;
        });
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "clinician-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL);
    }

    private ProblemEntity problem(String cpid) {
        ProblemEntity p = new ProblemEntity();
        p.setProblemId(UUID.randomUUID());
        p.setTenantId(TENANT_ID);
        p.setSubjectCpid(cpid);
        p.setDisplay("Type 2 diabetes mellitus");
        p.setCategory("DIAGNOSIS");
        p.setClinicalStatus("ACTIVE");
        p.setRecordedBy("clinician-1");
        lenient().when(problemRepository.findById(p.getProblemId())).thenReturn(Optional.of(p));
        return p;
    }

    private ProblemLinkEntity link(UUID problemId, Map<String, Object> body) {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            return service.link(problemId, body);
        }
    }

    private static Map<String, Object> body(String linkType, String targetType, String targetRef) {
        Map<String, Object> b = new HashMap<>();
        b.put("link_type", linkType);
        b.put("target_type", targetType);
        b.put("target_ref", targetRef);
        return b;
    }

    /** An external object is a typed reference, not a foreign key — it lives in another service. */
    @Test
    void linksAProblemToEvidenceOwnedByAnotherService() {
        ProblemEntity p = problem("CPID-9");
        ProblemLinkEntity l = link(p.getProblemId(),
                body("EVIDENCED_BY", "OBSERVATION", "obs-4417"));

        assertThat(l.getLinkType()).isEqualTo("EVIDENCED_BY");
        assertThat(l.getTargetType()).isEqualTo("OBSERVATION");
        assertThat(l.getTargetRef()).isEqualTo("obs-4417");
        assertThat(l.getTargetProblemId()).isNull();
    }

    /** Problem-to-problem is the one case with a real foreign key, because both ends are here. */
    @Test
    void linksAComplicationToTheDiseaseThatCausedIt() {
        ProblemEntity diabetes = problem("CPID-9");
        ProblemEntity nephropathy = problem("CPID-9");

        ProblemLinkEntity l = link(nephropathy.getProblemId(),
                body("COMPLICATION_OF", "PROBLEM", diabetes.getProblemId().toString()));

        assertThat(l.getTargetProblemId()).isEqualTo(diabetes.getProblemId());
        assertThat(l.getTargetRef()).isNull();
    }

    @Test
    void refusesToLinkAProblemToItself() {
        ProblemEntity p = problem("CPID-9");
        assertThatThrownBy(() -> link(p.getProblemId(),
                body("CAUSED_BY", "PROBLEM", p.getProblemId().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itself");
    }

    /** Linking across patients would attribute one person's disease to another's. */
    @Test
    void refusesToLinkAcrossPatients() {
        ProblemEntity mine = problem("CPID-9");
        ProblemEntity theirs = problem("CPID-OTHER");
        assertThatThrownBy(() -> link(mine.getProblemId(),
                body("COMORBID_WITH", "PROBLEM", theirs.getProblemId().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different patient");
    }

    @Test
    void refusesALinkTypeOutsideTheVocabulary() {
        ProblemEntity p = problem("CPID-9");
        assertThatThrownBy(() -> link(p.getProblemId(),
                body("RELATED_TO", "OBSERVATION", "obs-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesALinkWithNoTarget() {
        ProblemEntity p = problem("CPID-9");
        Map<String, Object> b = body("TREATED_BY", "MEDICATION", null);
        b.remove("target_ref");
        assertThatThrownBy(() -> link(p.getProblemId(), b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target_ref");
    }

    // ── The vocabularies must not drift from the migration ──────────────────────

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V103__problem_links.sql");

    @Test
    void linkTypesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ProblemLinkVocabulary.LINK_TYPES)
                .containsExactlyInAnyOrderElementsOf(checkValues("pct_problem_links_type_check"));
    }

    @Test
    void targetTypesMatchTheDatabaseCheckConstraint() throws IOException {
        assertThat(ProblemLinkVocabulary.TARGET_TYPES)
                .containsExactlyInAnyOrderElementsOf(checkValues("pct_problem_links_target_type_check"));
    }

    private static Set<String> checkValues(String constraintName) throws IOException {
        String sql = Files.readString(MIGRATION);
        int start = sql.indexOf("CONSTRAINT " + constraintName);
        assertThat(start).withFailMessage("constraint %s not found", constraintName).isGreaterThan(-1);
        int inStart = sql.indexOf("IN (", start);
        int inEnd = sql.indexOf("))", inStart);
        Set<String> values = new LinkedHashSet<>();
        Matcher m = Pattern.compile("'([A-Z_]+)'").matcher(sql.substring(inStart, inEnd));
        while (m.find()) values.add(m.group(1));
        assertThat(values).isNotEmpty();
        return values;
    }
}
