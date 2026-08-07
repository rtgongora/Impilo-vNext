package zw.gov.mohcc.impilo.tshepo.consent.service;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.tshepo.consent.config.ConsentProperties;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentAuditRepository;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentDirectiveEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentDirectiveRepository;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 gate condition #3: <em>a revoked consent demonstrably blocks a read</em>.
 *
 * <h2>Why this test exists when consent was already covered</h2>
 *
 * <p>Both halves of revocation were tested before this class, and neither half proved the gate:</p>
 *
 * <ul>
 *   <li>{@code ConsentEvaluationServiceTest} evaluates against a <em>mocked</em>
 *       {@code ConsentDirectiveRepository} and never constructs a REVOKED directive.</li>
 *   <li>{@code ConsentCrudServiceTest} revokes against a mocked repository and never
 *       evaluates afterwards.</li>
 * </ul>
 *
 * <p>The gap is not incidental. Revocation carries no logic of its own in the evaluator —
 * {@code revoke()} sets {@code status='REVOKED'}, and the <em>only</em> thing that turns that into
 * a refusal is the {@code AND c.status = 'ACTIVE'} predicate inside
 * {@link ConsentDirectiveRepository#findActiveForEvaluation}. That predicate lives in JPQL, so a
 * mocked repository cannot execute it: with a mock, the test author decides what revocation means
 * and then asserts their own decision. <strong>This test therefore runs against a real database</strong>
 * (H2, the profile the module's golden-contract test already boots with), a real repository, real
 * JPQL, and the real {@link ConsentCrudService#revoke} — so the status transition has to actually
 * survive a query to pass.</p>
 *
 * <h2>Where the seam is cut, and why</h2>
 *
 * <p>The PDP half of the chain — {@code PolicyEngine} turning a not-permitted
 * {@link ConsentDecision} into a DENY, and failing closed when this service is unreachable — is
 * proved in {@code ConsentRevocationDeniesAtPolicyEngineTest} in {@code tshepo-authz-service},
 * over real HTTP through the real {@code ConsentClient}. The two classes meet at the wire contract
 * that {@code ConsentClientContractTest} pins. They are separate because the modules are separate;
 * joining them in one JVM would mean adding a module dependency that production does not have.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Phase 0 D1: a revoked consent blocks the read it previously permitted")
class ConsentRevocationBlocksReadTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PATIENT = "cpid-12345";
    private static final String ACTOR = "practitioner-1";
    private static final String PURPOSE = "TREATMENT";

    /**
     * The scope the PDP actually asks for — {@code ConsentClient.DEFAULT_SCOPE}. Hard-coded rather
     * than imported because that constant lives in another module; if the two ever diverge,
     * evaluation returns NO_VALID_CONSENT for a subject who genuinely granted consent, which reads
     * as a refusal by the person rather than as two services using different vocabularies.
     */
    private static final String SCOPE = "clinical-data";

    @Autowired private ConsentDirectiveRepository consentRepo;
    @Autowired private ConsentAuditRepository auditRepo;
    @Autowired private EventOutboxRepository outboxRepo;

    private ConsentEvaluationService evaluation;
    private ConsentCrudService crud;
    private InMemoryConsentCache cache;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        cache = new InMemoryConsentCache();
        evaluation = new ConsentEvaluationService(consentRepo, cache, auditRepo, mapper);
        crud = new ConsentCrudService(
                consentRepo, auditRepo, outboxRepo,
                new FhirConsentMapper(FhirContext.forR4().newJsonParser()),
                cache, new ConsentProperties(), mapper);
    }

    // ════════════════════════════════════════════════════════════════════
    // The chained proof
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("grant -> PERMIT -> revoke -> DENY, through the real repository and real revoke()")
    void grantThenRevoke_flipsPermitToDeny() {
        UUID consentId = grantConsent();

        // 1. GRANTED: the read is permitted, and the decision names the directive that permitted it.
        ConsentDecision granted = evaluate();
        assertThat(granted.permitted())
                .as("a granted, in-period, scope-matching consent must permit the read")
                .isTrue();
        assertThat(granted.consentId()).isEqualTo(consentId.toString());

        // 2. REVOKE through production code — not by hand-setting the status, which would prove
        //    only that this test can write to a field.
        crud.revoke(consentId, "patient withdrew consent", PATIENT);

        // 3. REVOKED: the same read, unchanged in every other respect, is now refused.
        ConsentDecision afterRevocation = evaluate();
        assertThat(afterRevocation.permitted())
                .as("THE GATE: the identical read must be refused once consent is revoked")
                .isFalse();

        // The directive is gone from evaluation entirely, so the honest answer is "nobody has
        // granted this" rather than "the person said no" — a distinction PolicyEngine relies on to
        // choose between CONSENT_REQUIRED and CONSENT_DENIED.
        assertThat(afterRevocation.reason()).isEqualTo("NO_ACTIVE_CONSENT");

        // And the row is still there, soft-deleted: revocation must not destroy the record of what
        // was once permitted.
        ConsentDirectiveEntity persisted = consentRepo.findById(consentId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("REVOKED");
        assertThat(persisted.getRevokedAt()).isNotNull();
        assertThat(persisted.getRevocationReason()).isEqualTo("patient withdrew consent");
    }

    @Test
    @DisplayName("revocation evicts the cached PERMIT, so the refusal is immediate and not TTL-delayed")
    void revocationEvictsThePreviouslyCachedPermit() {
        UUID consentId = grantConsent();

        // Populate the cache with a PERMIT, the way a real prior read would have.
        assertThat(evaluate().permitted()).isTrue();
        assertThat(cache.size())
                .as("the PERMIT must actually be cached, or this test proves nothing about eviction")
                .isEqualTo(1);

        crud.revoke(consentId, "patient withdrew consent", PATIENT);

        assertThat(cache.evictions)
                .as("revoke() must invalidate the patient's cached decisions")
                .isEqualTo(1);
        assertThat(cache.size())
                .as("a cached PERMIT that survives revocation would serve access for the whole TTL")
                .isZero();
        assertThat(evaluate().permitted()).isFalse();
    }

    // ════════════════════════════════════════════════════════════════════
    // Negative control — the query predicate, not the test, is doing the work
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("negative control: an untouched grant keeps permitting, so the DENY above is revocation's doing")
    void withoutRevocation_theReadKeepsBeingPermitted() {
        grantConsent();

        assertThat(evaluate().permitted()).isTrue();
        cache.invalidateForPatient(TENANT, PATIENT);
        assertThat(evaluate().permitted())
                .as("re-evaluating from the database without revoking must still permit — "
                        + "otherwise the DENY in the chained test could come from anything")
                .isTrue();
    }

    @Test
    @DisplayName("revoking one purpose does not silently revoke another")
    void revocationIsScopedToTheDirectiveRevoked() {
        UUID treatmentConsent = grantConsent();
        grantConsent("PAYMENT");

        crud.revoke(treatmentConsent, "withdrew treatment consent", PATIENT);

        assertThat(evaluate().permitted())
                .as("the revoked purpose must be refused")
                .isFalse();
        assertThat(evaluation.evaluate(TENANT, ACTOR, PATIENT, "PAYMENT", SCOPE).permitted())
                .as("an unrelated, unrevoked consent must be untouched")
                .isTrue();
    }

    // ════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════

    private ConsentDecision evaluate() {
        return evaluation.evaluate(TENANT, ACTOR, PATIENT, PURPOSE, SCOPE);
    }

    private UUID grantConsent() {
        return grantConsent(PURPOSE);
    }

    /** Persists a real, active, in-period, scope-matching permit directive. */
    private UUID grantConsent(String purpose) {
        ConsentDirectiveEntity directive = new ConsentDirectiveEntity();
        directive.setTenantId(TENANT);
        directive.setPatientRef(PATIENT);
        directive.setGrantorRef(PATIENT);
        directive.setGranteeRef(ACTOR);
        directive.setStatus("ACTIVE");
        directive.setScope(SCOPE);
        directive.setPurpose(purpose);
        directive.setProvision("permit");
        directive.setFhirConsentJson("{\"resourceType\":\"Consent\",\"status\":\"active\"}");
        directive.setPeriodStart(Instant.now().minus(1, ChronoUnit.DAYS));
        directive.setPeriodEnd(Instant.now().plus(365, ChronoUnit.DAYS));
        return consentRepo.saveAndFlush(directive).getId();
    }

    /**
     * A real cache with real eviction semantics, held in memory.
     *
     * <p>Not a mock: it stores what it is given and returns it, and {@code invalidateForPatient}
     * genuinely drops the entries it tracked for that patient. That is what makes
     * {@link #revocationEvictsThePreviouslyCachedPermit} meaningful — a stubbed cache would let
     * eviction be asserted without eviction having to work.</p>
     *
     * <p>The production implementation is Redis-backed and swallows its own exceptions, so an
     * unreachable Redis degrades to a permanent cache miss. That is safe for access decisions
     * (every read falls through to the database) but it would make a cache test vacuously green,
     * which is the second reason not to lean on the real one here.</p>
     */
    private static final class InMemoryConsentCache extends ConsentCacheService {

        private final Map<String, ConsentDecision> entries = new HashMap<>();
        private final Map<String, Set<String>> keysByPatient = new HashMap<>();
        private int evictions;

        private InMemoryConsentCache() {
            super(null, null, null);
        }

        @Override
        public Optional<ConsentDecision> getCachedDecision(UUID tenantId, String patientRef,
                                                          String granteeRef, String purpose,
                                                          String scope) {
            return Optional.ofNullable(
                    entries.get(buildCacheKey(tenantId, patientRef, granteeRef, purpose, scope)));
        }

        @Override
        public void cacheDecision(UUID tenantId, String patientRef, String granteeRef,
                                  String purpose, String scope, ConsentDecision decision) {
            String key = buildCacheKey(tenantId, patientRef, granteeRef, purpose, scope);
            entries.put(key, decision);
            keysByPatient.computeIfAbsent(patientKey(tenantId, patientRef), k -> new HashSet<>()).add(key);
        }

        @Override
        public void invalidateForPatient(UUID tenantId, String patientRef) {
            evictions++;
            Set<String> keys = keysByPatient.remove(patientKey(tenantId, patientRef));
            if (keys != null) {
                keys.forEach(entries::remove);
            }
        }

        private static String patientKey(UUID tenantId, String patientRef) {
            return tenantId + ":" + patientRef;
        }

        private int size() {
            return entries.size();
        }
    }
}
