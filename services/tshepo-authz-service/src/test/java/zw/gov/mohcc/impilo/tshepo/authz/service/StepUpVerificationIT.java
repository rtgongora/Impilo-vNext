package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpChallengeResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.StepUpVerifyRequest;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.StepUpChallengeEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.StepUpChallengeRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runtime proof for the B1 step-up engine (G001) against a REAL Postgres.
 *
 * <p>Unlike {@code StepUpServiceTest} (which mocks the repository and audit boundary),
 * this boots the FULL Spring context against a real database, applies every Flyway
 * migration (including {@code V010__step_up_verification.sql}), and drives the
 * supervisor-approval mode end-to-end through real JPA round-trips. It proves three
 * things mocks cannot:</p>
 * <ol>
 *   <li><b>The migrations apply cleanly</b> — a duplicate {@code V002} version (the bug
 *       this proof first surfaced) fails Flyway at context startup, so a green boot proves
 *       the version line is sound and the new step-up columns
 *       (mode/attempt_count/approved_by/audit_metadata) exist.</li>
 *   <li><b>The context wires</b> — every step-up bean (dispatcher, providers config,
 *       AuditPublisher transactional outbox) constructs and injects.</li>
 *   <li><b>The fail-closed invariants hold against a real database</b> — no approval ⇒
 *       no completion; replay of a completed challenge is rejected by the pending-only
 *       lookup, not by an in-memory mock.</li>
 * </ol>
 *
 * <p><b>How to run:</b> point the harness at a real Postgres and override the datasource
 * coordinates, e.g.
 * {@code mvn test -Dtest=StepUpVerificationIT
 *   -Dit.pg.url=jdbc:postgresql://localhost:55432/test
 *   -Dit.pg.user=test -Dit.pg.pass=test}.
 * With no {@code it.pg.url} the test self-skips, so the mocked unit suite is unaffected.
 * (Testcontainers is deliberately not used here: this environment's docker-java client
 * cannot negotiate the engine's minimum API version, so the proof is driven against a
 * CLI-started / CI-service Postgres instead.)</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "it.pg.url", matches = ".+")
class StepUpVerificationIT {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("it.pg.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("it.pg.user"));
        registry.add("spring.datasource.password", () -> System.getProperty("it.pg.pass"));
    }

    @Autowired
    private StepUpService stepUpService;

    @Autowired
    private StepUpChallengeRepository challengeRepository;

    @Autowired
    private AuthzProperties properties;

    @Test
    void supervisorApproval_endToEnd_completesAndPersists() {
        UUID tenantId = UUID.randomUUID();
        String actor = "actor-" + UUID.randomUUID();

        StepUpChallengeResponse issued = stepUpService.issueChallenge(
                new StepUpChallengeRequest(tenantId, actor, "SUPERVISOR_APPROVAL"));
        assertThat(issued.status()).isEqualTo("PENDING");

        // Dual-control approval by an authorised, distinct supervisor principal.
        stepUpService.recordSupervisorApproval(issued.challengeId(), tenantId, "supervisor-1", true);

        StepUpChallengeResponse verified = stepUpService.verifyChallenge(
                new StepUpVerifyRequest(issued.challengeId(), tenantId, actor, "approved"));
        assertThat(verified.status()).isEqualTo("COMPLETED");

        // Read back through real JPA: persisted state matches, approval recorded, completion stamped.
        StepUpChallengeEntity persisted = challengeRepository.findById(issued.challengeId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("COMPLETED");
        assertThat(persisted.getApprovedBy()).isEqualTo("supervisor-1");
        assertThat(persisted.getCompletedAt()).isNotNull();
        assertThat(persisted.getMode()).isEqualTo("SUPERVISOR_APPROVAL");
    }

    @Test
    void supervisorApproval_withoutApproval_failsClosed() {
        UUID tenantId = UUID.randomUUID();
        String actor = "actor-" + UUID.randomUUID();

        StepUpChallengeResponse issued = stepUpService.issueChallenge(
                new StepUpChallengeRequest(tenantId, actor, "SUPERVISOR_APPROVAL"));

        // No supervisor approval recorded ⇒ verify must NOT complete.
        assertThatThrownBy(() -> stepUpService.verifyChallenge(
                new StepUpVerifyRequest(issued.challengeId(), tenantId, actor, "approved")))
                .isInstanceOf(SecurityException.class);

        StepUpChallengeEntity persisted = challengeRepository.findById(issued.challengeId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("PENDING");
        assertThat(persisted.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void failedAttempts_persistAndLockOut() {
        UUID tenantId = UUID.randomUUID();
        String actor = "actor-" + UUID.randomUUID();
        int cap = properties.getMaxStepUpAttempts();

        StepUpChallengeResponse issued = stepUpService.issueChallenge(
                new StepUpChallengeRequest(tenantId, actor, "SUPERVISOR_APPROVAL"));

        // `cap` failed attempts: each must be counted and the count must PERSIST across
        // attempts (the bug this proof caught: the increment was rolled back with the
        // throwing transaction, so the counter never advanced and lockout never fired).
        for (int i = 1; i <= cap; i++) {
            assertThatThrownBy(() -> stepUpService.verifyChallenge(
                    new StepUpVerifyRequest(issued.challengeId(), tenantId, actor, "wrong")))
                    .isInstanceOf(SecurityException.class);
            assertThat(challengeRepository.findById(issued.challengeId()).orElseThrow().getAttemptCount())
                    .as("attempt count must persist across failed attempts")
                    .isEqualTo(i);
        }

        // Next attempt exceeds the cap ⇒ challenge is locked (FAILED), not retryable.
        assertThatThrownBy(() -> stepUpService.verifyChallenge(
                new StepUpVerifyRequest(issued.challengeId(), tenantId, actor, "wrong")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("attempt limit");
        assertThat(challengeRepository.findById(issued.challengeId()).orElseThrow().getStatus())
                .isEqualTo("FAILED");
    }

    @Test
    void completedChallenge_cannotBeReplayed() {
        UUID tenantId = UUID.randomUUID();
        String actor = "actor-" + UUID.randomUUID();

        StepUpChallengeResponse issued = stepUpService.issueChallenge(
                new StepUpChallengeRequest(tenantId, actor, "SUPERVISOR_APPROVAL"));
        stepUpService.recordSupervisorApproval(issued.challengeId(), tenantId, "supervisor-1", true);
        stepUpService.verifyChallenge(
                new StepUpVerifyRequest(issued.challengeId(), tenantId, actor, "approved"));

        // Second verify of an already-completed challenge is rejected by the pending-only lookup.
        assertThatThrownBy(() -> stepUpService.verifyChallenge(
                new StepUpVerifyRequest(issued.challengeId(), tenantId, actor, "approved")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
