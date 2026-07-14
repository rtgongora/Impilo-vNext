package zw.gov.mohcc.impilo.ia.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import zw.gov.mohcc.impilo.ia.persistence.entity.AssuranceUpgradeRequestEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runtime proof for the C2a assurance workflow against a REAL Postgres. Boots the full
 * context, applies V002, and drives the workflow through real JPA: status defaults to LOA1;
 * an upgrade request → reviewer approval raises the level; the reject path records a reason
 * and does not raise; dual-control blocks a self-review. Self-skips without -Dit.pg.url.
 */
@SpringBootTest
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "it.pg.url", matches = ".+")
class AssuranceWorkflowRuntimeProofIT {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("it.pg.url"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> System.getProperty("it.pg.user"));
        registry.add("spring.datasource.password", () -> System.getProperty("it.pg.pass"));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private AssuranceService service;

    @Test
    void upgradeApproval_raisesLevel() {
        UUID tenant = UUID.randomUUID();
        String actor = "citizen-" + UUID.randomUUID();

        assertThat(service.getStatus(tenant, actor).currentLevel()).isEqualTo(AssuranceLevel.LOA1);

        AssuranceUpgradeRequestEntity req = service.requestUpgrade(tenant, actor, AssuranceLevel.LOA3, "IN_PERSON_VERIFICATION");
        assertThat(req.getStatus()).isEqualTo(UpgradeStatus.PENDING);
        assertThat(service.listPending(tenant)).extracting(AssuranceUpgradeRequestEntity::getId).contains(req.getId());

        // A different reviewer approves → level is raised and reflected on the next status read.
        service.decideUpgrade(tenant, "reviewer-officer", req.getId(), true, null);
        assertThat(service.getStatus(tenant, actor).currentLevel()).isEqualTo(AssuranceLevel.LOA3);
        assertThat(service.getStatus(tenant, actor).grantedPermissions()).contains("PRESCRIBING");
    }

    @Test
    void rejectPath_recordsReason_andDoesNotRaise() {
        UUID tenant = UUID.randomUUID();
        String actor = "citizen-" + UUID.randomUUID();
        AssuranceUpgradeRequestEntity req = service.requestUpgrade(tenant, actor, AssuranceLevel.LOA3, "SUPERVISED_REMOTE_PROOFING");

        AssuranceUpgradeRequestEntity decided = service.decideUpgrade(tenant, "reviewer-officer", req.getId(), false, "Document mismatch");
        assertThat(decided.getStatus()).isEqualTo(UpgradeStatus.REJECTED);
        assertThat(decided.getReason()).isEqualTo("Document mismatch");
        assertThat(service.getStatus(tenant, actor).currentLevel()).isEqualTo(AssuranceLevel.LOA1);
    }

    @Test
    void dualControl_selfReview_isBlocked() {
        UUID tenant = UUID.randomUUID();
        String actor = "citizen-" + UUID.randomUUID();
        AssuranceUpgradeRequestEntity req = service.requestUpgrade(tenant, actor, AssuranceLevel.LOA3, "IN_PERSON_VERIFICATION");
        assertThatThrownBy(() -> service.decideUpgrade(tenant, actor, req.getId(), true, null))
                .isInstanceOf(SecurityException.class);
    }
}
