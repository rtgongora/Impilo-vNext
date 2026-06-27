package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.StepUpChallengeEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustAuthorityEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustDocumentSignerCertEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TrustIssuerSystemEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.StepUpChallengeRepository;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustEnvironment;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustReadiness;
import zw.gov.mohcc.impilo.tshepo.authz.trust.TrustStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runtime proof for the B5 Trust Authority registry against a REAL Postgres.
 *
 * <p>Boots the full tshepo-authz context, applies the V014 migration, and drives the
 * registry end-to-end through real JPA: the admin+step-up gate (with a seeded recent
 * step-up), create→list, a legal status transition, readiness update, and issuer /
 * document-signer registration — plus the fail-closed path when no step-up exists.
 * Harness-driven via {@code scripts/runtime-proof/tshepo-authz-stepup.sh} with
 * {@code -Dtest=TrustAuthorityRegistryRuntimeProofIT}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "it.pg.url", matches = ".+")
class TrustAuthorityRegistryRuntimeProofIT {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("it.pg.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("it.pg.user"));
        registry.add("spring.datasource.password", () -> System.getProperty("it.pg.pass"));
    }

    @Autowired private TrustAuthorityService service;
    @Autowired private StepUpChallengeRepository challengeRepository;

    private static final List<String> ADMIN = List.of("SYSTEM_ADMIN");

    /** Seed a recently-completed step-up so the dual gate's step-up condition is satisfied. */
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
    void fullLifecycle_withAdminAndStepUp() {
        UUID tenant = UUID.randomUUID();
        String actor = "admin-" + UUID.randomUUID();
        seedRecentStepUp(tenant, actor);

        TrustAuthorityEntity authority = service.createAuthority(
                tenant, actor, ADMIN, "ZW National Health Trust", "health.gov.zw", TrustEnvironment.UAT);
        assertThat(authority.getId()).isNotNull();
        assertThat(authority.getStatus()).isEqualTo(TrustStatus.DRAFT);

        // Appears in the tenant listing (real JPA round-trip).
        assertThat(service.listAuthorities(tenant)).extracting(TrustAuthorityEntity::getId).contains(authority.getId());

        // Legal transition + readiness update persist.
        TrustAuthorityEntity pending = service.transitionStatus(tenant, actor, ADMIN, authority.getId(), TrustStatus.PENDING);
        assertThat(pending.getStatus()).isEqualTo(TrustStatus.PENDING);
        TrustAuthorityEntity ready = service.updateReadiness(tenant, actor, ADMIN, authority.getId(),
                TrustReadiness.FOUNDATION_IN_PROGRESS);
        assertThat(ready.getReadiness()).isEqualTo(TrustReadiness.FOUNDATION_IN_PROGRESS);

        // Issuer + document-signer register under the authority and read back.
        TrustIssuerSystemEntity issuer = service.addIssuer(tenant, actor, ADMIN, authority.getId(),
                "National Issuing Service", "https://issuer.health.gov.zw");
        assertThat(service.listIssuers(tenant, authority.getId())).extracting(TrustIssuerSystemEntity::getId)
                .contains(issuer.getId());

        TrustDocumentSignerCertEntity dsc = service.addDocumentSigner(tenant, actor, ADMIN, authority.getId(),
                "kid-dsc-1", "CN=ZW DSC", Instant.now(), Instant.now().plusSeconds(31_536_000L));
        assertThat(service.listDocumentSigners(tenant, authority.getId())).extracting(TrustDocumentSignerCertEntity::getId)
                .contains(dsc.getId());
    }

    @Test
    void create_withoutStepUp_failsClosed() {
        UUID tenant = UUID.randomUUID();
        String actor = "admin-" + UUID.randomUUID(); // no step-up seeded
        assertThatThrownBy(() -> service.createAuthority(
                tenant, actor, ADMIN, "x", "y", TrustEnvironment.TEST))
                .isInstanceOf(SecurityException.class);
        assertThat(service.listAuthorities(tenant)).isEmpty();
    }
}
