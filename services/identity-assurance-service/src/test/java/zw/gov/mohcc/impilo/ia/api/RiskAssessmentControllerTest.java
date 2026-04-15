package zw.gov.mohcc.impilo.ia.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.ia.config.TestSecurityConfig;
import zw.gov.mohcc.impilo.ia.core.Recommendation;
import zw.gov.mohcc.impilo.ia.core.RiskAssessmentService;
import zw.gov.mohcc.impilo.ia.core.RiskLevel;
import zw.gov.mohcc.impilo.ia.persistence.entity.RiskAssessmentEntity;

@WebMvcTest(RiskAssessmentController.class)
@Import(TestSecurityConfig.class)
class RiskAssessmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RiskAssessmentService riskAssessmentService;

    @Test
    void postAssess_returns201() throws Exception {
        RiskAssessmentEntity saved = new RiskAssessmentEntity();
        saved.setId(1L);
        saved.setTenantId(UUID.randomUUID());
        saved.setActorId("user-1");
        saved.setContextType("LOGIN");
        saved.setRiskScore(new BigDecimal("0.2500"));
        saved.setRiskLevel(RiskLevel.LOW);
        saved.setRecommendation(Recommendation.ALLOW);
        saved.setAssessedBy("user-1");

        when(riskAssessmentService.assess(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);

        mockMvc.perform(post("/internal/v1/risk/assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "user-1")
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .content("{\"contextType\":\"LOGIN\",\"riskScore\":0.25,\"riskLevel\":\"LOW\",\"recommendation\":\"ALLOW\"}"))
                .andExpect(status().isCreated());
    }
}
