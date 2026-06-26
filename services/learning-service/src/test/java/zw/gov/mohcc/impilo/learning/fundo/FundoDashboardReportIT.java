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
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;

/** B4 — programme + regulator dashboard summaries. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoDashboardReportIT {

    private static final UUID TENANT = UUID.fromString("b4b4b4b4-0000-4000-8000-000000000001");

    @Autowired private FundoReportService reports;
    @Autowired private FundoLearningProviderService providers;
    @Autowired private CourseRepository courseRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @SuppressWarnings("unchecked")
    @Test
    void programmeAndRegulatorSummaries() {
        CourseEntity c = new CourseEntity();
        c.setTenantId(TENANT);
        c.setCode("PROG-1");
        c.setTitle("Prog course");
        c.setStatus("PUBLISHED");
        c.setLanguage("en");
        c.setCategory("MATERNAL_HEALTH");
        courseRepository.save(c);

        Map<String, Object> prog = reports.programmeSummary(TENANT);
        assertThat(((List<?>) prog.get("items"))).isNotEmpty();

        providers.createProvider(TENANT, "admin", Map.of("providerKind", "ORGANISATION", "displayName", "Org A"));
        Map<String, Object> reg = reports.regulatorSummary(TENANT);
        assertThat(reg.get("totalProviders")).isEqualTo(1);
        assertThat(((List<?>) reg.get("items"))).isNotEmpty();
    }
}
