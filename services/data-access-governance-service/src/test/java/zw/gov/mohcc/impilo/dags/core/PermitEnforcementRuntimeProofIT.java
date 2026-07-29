package zw.gov.mohcc.impilo.dags.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import zw.gov.mohcc.impilo.dags.core.EnforcementService.PermitContext;
import zw.gov.mohcc.impilo.dags.core.EnforcementService.Reason;
import zw.gov.mohcc.impilo.dags.persistence.entity.AccessRequestEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.AccessRequestRepository;
import zw.gov.mohcc.impilo.dags.persistence.repository.PermitReplayRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runtime proof for the B3 permit engine (G003, G056) against a REAL Postgres.
 *
 * <p>Boots the full DAGS context, applies the Flyway migrations (V001 + the new
 * V002 permit_replay + widened permit_token column), and proves end-to-end that:
 * approve() persists a full-length v2 token; verifyAndConsume validates a real token and
 * <b>consumes its nonce in the database</b>; a second presentation is rejected as REPLAYED
 * (via the unique constraint, not an in-memory mock); and a tampered token is rejected.
 * The H2 unit suite mocks the replay store — only this proves the real persistence + the
 * migration. Harness-driven: see {@code scripts/runtime-proof/dags-permit-enforcement.sh}.</p>
 */
@SpringBootTest
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "it.pg.url", matches = ".+")
class PermitEnforcementRuntimeProofIT {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        // Override the H2 test profile with the harness-provided Postgres + Flyway.
        registry.add("spring.datasource.url", () -> System.getProperty("it.pg.url"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> System.getProperty("it.pg.user"));
        registry.add("spring.datasource.password", () -> System.getProperty("it.pg.pass"));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private EnforcementService enforcementService;
    @Autowired private AccessRequestService accessRequestService;
    @Autowired private AccessRequestRepository requestRepository;
    @Autowired private PermitReplayRepository replayRepository;

    private AccessRequestEntity saveSubmitted(UUID tenant, String requester, String resType, String resId, String purpose) {
        AccessRequestEntity e = new AccessRequestEntity();
        e.setTenantId(tenant);
        e.setRequesterId(requester);
        e.setResourceType(resType);
        e.setResourceId(resId);
        e.setPurpose(purpose);
        e.setStatus(AccessRequestStatus.SUBMITTED);
        return requestRepository.saveAndFlush(e);
    }

    @Test
    void approve_persistsFullLengthV2Token() {
        UUID tenant = UUID.randomUUID();
        AccessRequestEntity saved = saveSubmitted(tenant, "dr-jones", "PATIENT_RECORD", "patient-42", "TREATMENT");

        AccessRequestEntity approved = accessRequestService.approve(
                saved.getId(), tenant, "approver-1", UUID.randomUUID(), "meets criteria");

        assertThat(approved.getPermitToken()).startsWith("permit-token:v2:");
        // Read back through real JPA: the widened column persisted the full token.
        AccessRequestEntity reloaded = requestRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPermitToken()).isEqualTo(approved.getPermitToken());
        assertThat(reloaded.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
    }

    @Test
    void verify_thenReplay_isRejectedViaDatabase() {
        UUID tenant = UUID.randomUUID();
        AccessRequestEntity e = saveSubmitted(tenant, "dr-jones", "PATIENT_RECORD", "patient-7", "TREATMENT");
        String token = enforcementService.issuePermitToken(e);
        PermitContext context = new PermitContext(tenant, "dr-jones", "PATIENT_RECORD", "patient-7", "TREATMENT");

        // First use: valid, and the nonce is consumed in the DB.
        assertThat(enforcementService.verifyAndConsume(token, context).reason()).isEqualTo(Reason.VALID);
        assertThat(replayRepository.count()).isPositive();

        // Replay of the same token is rejected by the persisted nonce — not an in-memory mock.
        assertThat(enforcementService.verifyAndConsume(token, context).reason()).isEqualTo(Reason.REPLAYED);
    }

    @Test
    void tamperedToken_isRejected() {
        UUID tenant = UUID.randomUUID();
        AccessRequestEntity e = saveSubmitted(tenant, "dr-jones", "PATIENT_RECORD", "patient-9", "TREATMENT");
        String token = enforcementService.issuePermitToken(e);
        PermitContext context = new PermitContext(tenant, "dr-jones", "PATIENT_RECORD", "patient-9", "TREATMENT");

        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertThat(enforcementService.verifyAndConsume(tampered, context).reason()).isEqualTo(Reason.INVALID_SIGNATURE);
    }
}
