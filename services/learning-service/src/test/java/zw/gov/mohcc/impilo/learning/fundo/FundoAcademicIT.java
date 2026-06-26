package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;

/** A4 — academic program + term administration against H2. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoAcademicIT {

    private static final UUID TENANT = UUID.fromString("a4a4a4a4-0000-4000-8000-000000000001");

    @Autowired private FundoAcademicService academic;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @SuppressWarnings("unchecked")
    @Test
    void programAndTermLifecycle() {
        Map<String, Object> program = academic.createProgram(TENANT, "registrar", Map.of(
                "code", "DIP-NURSING", "title", "Diploma in Nursing", "qualificationLevel", "DIPLOMA", "durationTerms", 6));
        String programId = (String) program.get("id");
        assertThat(((List<?>) academic.listPrograms(TENANT, 50).get("items"))).hasSize(1);

        Map<String, Object> term = academic.createTerm(TENANT, "registrar", Map.of(
                "programId", programId, "name", "Term 1 2026", "academicYear", "2026", "termNo", 1,
                "startsOn", "2026-01-12", "endsOn", "2026-04-10",
                "registrationOpensAt", "2025-12-01T00:00:00Z", "registrationClosesAt", "2026-01-11T00:00:00Z"));
        UUID termId = UUID.fromString((String) term.get("id"));
        assertThat(term.get("startsOn")).isEqualTo("2026-01-12");

        assertThat(((List<?>) academic.listTerms(TENANT, programId, 50).get("items"))).hasSize(1);
        // registration window closed (now is well past 2026-01-11)
        assertThat(academic.registrationOpen(TENANT, termId)).isFalse();
    }
}
