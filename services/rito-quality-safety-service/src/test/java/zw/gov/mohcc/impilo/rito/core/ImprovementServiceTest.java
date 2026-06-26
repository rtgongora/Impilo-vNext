package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.persistence.entity.CorrectiveActionEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.QiPlanEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.QiTaskEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ImprovementServiceTest {

    @Autowired private ImprovementService improvementService;

    @Test
    void correctiveActionActVerifyClosure() {
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();

        CorrectiveActionEntity ca = improvementService.createCorrectiveAction(tenant, "CORRECTIVE",
                "Fix signage", "desc", "CASE", null, null, null, facility,
                "owner-1", "OPERATOR", "HIGH", null, "creator-1");
        assertThat(ca.getCaReference()).startsWith("RITO-CA-");
        assertThat(ca.getStatus()).isEqualTo("OPEN");

        CorrectiveActionEntity completed = improvementService.complete(tenant, ca.getId(), "owner-1", "done");
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getCompletedAt()).isNotNull();

        CorrectiveActionEntity verified = improvementService.verify(tenant, ca.getId(),
                "verifier-1", "EFFECTIVE", "checked");
        assertThat(verified.getStatus()).isEqualTo("VERIFIED");
        assertThat(verified.getVerificationOutcome()).isEqualTo("EFFECTIVE");
    }

    @Test
    void verifyRejectsUnknownOutcome() {
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();

        CorrectiveActionEntity ca = improvementService.createCorrectiveAction(tenant, "CORRECTIVE",
                "Fix signage", "desc", "CASE", null, null, null, facility,
                "owner-1", "OPERATOR", "HIGH", null, "creator-1");
        improvementService.complete(tenant, ca.getId(), "owner-1", "done");

        assertThatThrownBy(() ->
                improvementService.verify(tenant, ca.getId(), "verifier-1", "MAYBE", "checked"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void qiPlanPdsaLifecycleToCompleted() {
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();

        QiPlanEntity plan = improvementService.createQiPlan(tenant, "Reduce waits", "aim", "PDSA",
                "problem", null, null, null, facility, null, null, null, null, null, "creator-1");

        QiTaskEntity task = improvementService.addTask(tenant, plan.getId(), "Plan change",
                "describe", "PLAN", "owner-1", 0, null);
        improvementService.updateTask(tenant, task.getId(), "DONE", null);

        QiPlanEntity completed = improvementService.completeQiPlan(tenant, plan.getId(), "improved");
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
    }
}
