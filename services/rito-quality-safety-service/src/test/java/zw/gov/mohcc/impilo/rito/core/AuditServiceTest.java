package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditToolEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuditServiceTest {

    @Autowired private AuditService auditService;

    @Test
    void submitAuditComputesComplianceScoreExcludingNaAndObservation() {
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();

        AuditToolEntity tool = auditService.createTool(tenant, "supervision-v1", "Supervision",
                null, null, "SUPPORTIVE_SUPERVISION", null, null);

        AuditEntity audit = auditService.createAudit(tenant, tool.getId(), "SUPPORTIVE_SUPERVISION",
                facility, null, null, "auditor-1", null, "auditor-1");

        AuditEntity started = auditService.startAudit(tenant, audit.getId());
        assertThat(started.getStatus()).isEqualTo("IN_PROGRESS");

        auditService.addFinding(tenant, audit.getId(), "s1", "i1", "COMPLIANT", "LOW", "ok", null, null, null);
        auditService.addFinding(tenant, audit.getId(), "s1", "i2", "COMPLIANT", "LOW", "ok", null, null, null);
        auditService.addFinding(tenant, audit.getId(), "s1", "i3", "PARTIAL", "MODERATE", "partial", null, null, null);
        auditService.addFinding(tenant, audit.getId(), "s1", "i4", "NON_COMPLIANT", "HIGH", "gap", null, null, null);
        auditService.addFinding(tenant, audit.getId(), "s1", "i5", "NOT_APPLICABLE", null, "n/a", null, null, null);

        AuditEntity submitted = auditService.submitAudit(tenant, audit.getId());

        assertThat(submitted.getStatus()).isEqualTo("SUBMITTED");
        // denominator = 4 (2 compliant + 1 partial + 1 non_compliant); numerator = 2 + 0.5 = 2.5; percent = 62.50
        assertThat(submitted.getScorePercent()).isEqualByComparingTo("62.50");
    }
}
