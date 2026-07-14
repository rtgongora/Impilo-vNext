package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.StepUpChallengeEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.StepUpChallengeRepository;
import zw.gov.mohcc.impilo.tshepo.authz.trust.GdhcnReadinessDomain;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustReadiness;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runtime proof for the B6 GDHCN readiness cockpit backend against a REAL Postgres.
 * Boots the full context, applies V015, and proves: list returns the full honest catalogue;
 * an admin+step-up update upserts and is reflected on the next read; and the fail-closed path
 * when no step-up exists. Self-skips without {@code -Dit.pg.url}.
 */
@SpringBootTest
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "it.pg.url", matches = ".+")
class GdhcnReadinessRuntimeProofIT {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("it.pg.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("it.pg.user"));
        registry.add("spring.datasource.password", () -> System.getProperty("it.pg.pass"));
    }

    @Autowired private GdhcnReadinessService service;
    @Autowired private StepUpChallengeRepository challengeRepository;

    private static final List<String> ADMIN = List.of("SYSTEM_ADMIN");

    private void seedRecentStepUp(UUID tenantId, String actorId) {
        StepUpChallengeEntity c = new StepUpChallengeEntity();
        c.setTenantId(tenantId);
        c.setActorId(actorId);
        c.setChallengeType("SUPERVISOR_APPROVAL");
        c.setStatus("COMPLETED");
        c.setIssuedAt(Instant.now());
        c.setExpiresAt(Instant.now().plusSeconds(300));
        c.setCompletedAt(Instant.now());
        challengeRepository.save(c);
    }

    @Test
    void list_thenGatedUpdate_isReflected() {
        UUID tenant = UUID.randomUUID();
        String actor = "admin-" + UUID.randomUUID();
        seedRecentStepUp(tenant, actor);

        // Full honest catalogue, all NOT_STARTED.
        List<GdhcnReadinessService.DomainReadiness> initial = service.listReadiness(tenant);
        assertThat(initial).hasSize(GdhcnReadinessDomain.values().length);
        assertThat(initial).allSatisfy(d -> assertThat(d.currentMaturity()).isEqualTo(TrustReadiness.NOT_STARTED));

        // Admin + step-up update persists.
        service.updateDomain(tenant, actor, ADMIN, "SIGNATURE_VERIFICATION",
                TrustReadiness.FOUNDATION_IN_PROGRESS, TrustReadiness.PRODUCTION_NOT_READY,
                "Trust Office", "tshepo-trust-crypto landed", "in progress", null);

        // Reflected on the next read (real JPA round-trip).
        GdhcnReadinessService.DomainReadiness updated = service.listReadiness(tenant).stream()
                .filter(d -> d.domainKey().equals("SIGNATURE_VERIFICATION")).findFirst().orElseThrow();
        assertThat(updated.currentMaturity()).isEqualTo(TrustReadiness.FOUNDATION_IN_PROGRESS);
        assertThat(updated.owner()).isEqualTo("Trust Office");
    }

    @Test
    void update_withoutStepUp_failsClosed() {
        UUID tenant = UUID.randomUUID();
        String actor = "admin-" + UUID.randomUUID(); // no step-up seeded
        assertThatThrownBy(() -> service.updateDomain(tenant, actor, ADMIN, "GOVERNANCE",
                TrustReadiness.ASSESSMENT_BASELINE, null, null, null, null, null))
                .isInstanceOf(SecurityException.class);
    }
}
