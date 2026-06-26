package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.persistence.entity.SurveyEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.SurveyResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SurveyServiceTest {

    @Autowired private SurveyService surveyService;

    @Test
    void lowCsatSpawnsFollowUpCaseAndAggregatesHonestly() {
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();

        SurveyEntity survey = surveyService.createSurvey(tenant, "post-visit-csat", "Post visit",
                null, "CSAT", null, List.of("How was your visit?"), Boolean.TRUE);

        SurveyResponseEntity low = surveyService.submitResponse(tenant, survey.getId(), "client-1",
                "CITIZEN", false, facility, null, Map.of("score", 1), "Rude staff");
        assertThat(low.getCaseId()).isNotNull();
        assertThat(low.getScore()).isEqualByComparingTo("1");

        SurveyResponseEntity high = surveyService.submitResponse(tenant, survey.getId(), "client-2",
                "CITIZEN", false, facility, null, Map.of("score", 5), null);
        assertThat(high.getCaseId()).isNull();

        Map<String, Object> agg = surveyService.aggregate(tenant, survey.getId());
        assertThat(((Number) agg.get("totalResponses")).longValue()).isEqualTo(2L);
    }
}
