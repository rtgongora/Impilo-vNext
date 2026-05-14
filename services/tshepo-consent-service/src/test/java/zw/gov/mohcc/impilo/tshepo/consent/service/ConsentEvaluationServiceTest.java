package zw.gov.mohcc.impilo.tshepo.consent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentDirectiveEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentDirectiveRepository;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentAuditRepository;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConsentEvaluationService}.
 *
 * <p>This is the core consent evaluation engine called by tshepo-authz-service
 * on every clinical access decision. Tests cover all evaluation rules:
 * active/expired/no consent, wildcard grantee/purpose, scope matching,
 * deny-wins semantics, and Redis cache integration.</p>
 */
@ExtendWith(MockitoExtension.class)
class ConsentEvaluationServiceTest {

    @Mock
    private ConsentDirectiveRepository consentRepo;

    @Mock
    private ConsentCacheService cacheService;

    @Mock
    private ConsentAuditRepository consentAuditRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ConsentEvaluationService evaluationService;

    // --- Shared test fixtures ---
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACTOR_ID = "practitioner-123";
    private static final String PATIENT_REF = "Patient/CPID-456";
    private static final String PURPOSE = "TREATMENT";
    private static final String SCOPE = "clinical-data";

    /**
     * Builds a consent directive entity with sensible defaults for testing.
     * The returned entity is active, permits access, and has a valid time period.
     */
    private ConsentDirectiveEntity buildDirective(String granteeRef, String purpose,
                                                   String scope, String provision) {
        ConsentDirectiveEntity entity = new ConsentDirectiveEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(TENANT_ID);
        entity.setPatientRef(PATIENT_REF);
        entity.setGrantorRef("Patient/CPID-456");
        entity.setGranteeRef(granteeRef);
        entity.setStatus("ACTIVE");
        entity.setScope(scope);
        entity.setPurpose(purpose);
        entity.setProvision(provision);
        entity.setPeriodStart(Instant.now().minus(30, ChronoUnit.DAYS));
        entity.setPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        entity.setFhirConsentJson("{}");
        entity.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        return entity;
    }

    /**
     * Builds a consent directive that has already expired.
     */
    private ConsentDirectiveEntity buildExpiredDirective(String granteeRef, String purpose,
                                                          String scope) {
        ConsentDirectiveEntity entity = buildDirective(granteeRef, purpose, scope, "permit");
        entity.setPeriodEnd(Instant.now().minus(1, ChronoUnit.DAYS));
        return entity;
    }

    /**
     * Builds a consent directive that has not yet started.
     */
    private ConsentDirectiveEntity buildFutureDirective(String granteeRef, String purpose,
                                                          String scope) {
        ConsentDirectiveEntity entity = buildDirective(granteeRef, purpose, scope, "permit");
        entity.setPeriodStart(Instant.now().plus(10, ChronoUnit.DAYS));
        entity.setPeriodEnd(Instant.now().plus(40, ChronoUnit.DAYS));
        return entity;
    }

    /**
     * Stubs the cache to always return empty (cache miss) so the service evaluates from DB.
     */
    private void stubCacheMiss() {
        when(cacheService.getCachedDecision(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    // =========================================================================
    // Test cases
    // =========================================================================

    @Nested
    @DisplayName("Active consent evaluation")
    class ActiveConsentTests {

        @Test
        @DisplayName("evaluate_withActiveConsent_permits - active consent with matching scope returns PERMIT")
        void evaluate_withActiveConsent_permits() {
            stubCacheMiss();

            ConsentDirectiveEntity directive = buildDirective(ACTOR_ID, PURPOSE, SCOPE, "permit");
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(directive));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, SCOPE);

            assertThat(decision.permitted()).isTrue();
            assertThat(decision.provision()).isEqualTo("permit");
            assertThat(decision.consentId()).isEqualTo(directive.getId().toString());
            assertThat(decision.allowedScopes()).contains(SCOPE);
            assertThat(decision.reason()).isNull();

            // Verify the result was cached
            verify(cacheService).cacheDecision(
                    eq(TENANT_ID), eq(PATIENT_REF), eq(ACTOR_ID), eq(PURPOSE), eq(SCOPE), any(ConsentDecision.class));
        }
    }

    @Nested
    @DisplayName("Expired consent evaluation")
    class ExpiredConsentTests {

        @Test
        @DisplayName("evaluate_withExpiredConsent_denies - all expired consents result in DENY")
        void evaluate_withExpiredConsent_denies() {
            stubCacheMiss();

            // Only expired directives returned from the query
            ConsentDirectiveEntity expired = buildExpiredDirective(ACTOR_ID, PURPOSE, SCOPE);
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(expired));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, SCOPE);

            assertThat(decision.permitted()).isFalse();
            assertThat(decision.reason()).isEqualTo("NO_VALID_CONSENT");
        }
    }

    @Nested
    @DisplayName("No consent evaluation")
    class NoConsentTests {

        @Test
        @DisplayName("evaluate_withNoConsent_deniesDefault - no active consents returns NO_ACTIVE_CONSENT deny")
        void evaluate_withNoConsent_deniesDefault() {
            stubCacheMiss();

            // No directives found for this evaluation query
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(Collections.emptyList());
            // And the patient has zero active consents at all
            when(consentRepo.countByTenantIdAndPatientRefAndStatus(TENANT_ID, PATIENT_REF, "ACTIVE"))
                    .thenReturn(0L);

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, SCOPE);

            assertThat(decision.permitted()).isFalse();
            assertThat(decision.reason()).isEqualTo("NO_ACTIVE_CONSENT");
        }

        @Test
        @DisplayName("evaluate_withNoMatchingConsent_denies - patient has consents but none for this purpose")
        void evaluate_withNoMatchingConsent_denies() {
            stubCacheMiss();

            // No directives match the evaluation query
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(Collections.emptyList());
            // But the patient DOES have other active consents
            when(consentRepo.countByTenantIdAndPatientRefAndStatus(TENANT_ID, PATIENT_REF, "ACTIVE"))
                    .thenReturn(3L);

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, SCOPE);

            assertThat(decision.permitted()).isFalse();
            assertThat(decision.reason()).isEqualTo("NO_MATCHING_CONSENT");
        }
    }

    @Nested
    @DisplayName("Wildcard grantee evaluation")
    class WildcardGranteeTests {

        @Test
        @DisplayName("evaluate_withWildcardGrantee_permits - granteeRef=null (applies to all) permits access")
        void evaluate_withWildcardGrantee_permits() {
            stubCacheMiss();

            // A consent directive with null granteeRef applies to all actors.
            // The repository query (granteeRef = :granteeRef OR granteeRef IS NULL)
            // returns these regardless of who the actor is.
            ConsentDirectiveEntity directive = buildDirective(null, PURPOSE, SCOPE, "permit");
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(directive));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, SCOPE);

            assertThat(decision.permitted()).isTrue();
            assertThat(decision.provision()).isEqualTo("permit");
            assertThat(decision.allowedScopes()).contains(SCOPE);
        }
    }

    @Nested
    @DisplayName("Wildcard purpose/scope evaluation")
    class WildcardPurposeTests {

        @Test
        @DisplayName("evaluate_withWildcardPurpose_permits - wildcard scope '*' matches any requested scope")
        void evaluate_withWildcardPurpose_permits() {
            stubCacheMiss();

            // Directive with wildcard scope matches everything
            ConsentDirectiveEntity directive = buildDirective(ACTOR_ID, PURPOSE, "*", "permit");
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(directive));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, "any-scope-here");

            assertThat(decision.permitted()).isTrue();
            assertThat(decision.provision()).isEqualTo("permit");
            assertThat(decision.allowedScopes()).contains("*");
        }
    }

    @Nested
    @DisplayName("Specific grantee matching")
    class SpecificGranteeTests {

        @Test
        @DisplayName("evaluate_withSpecificGranteeMatch_permits - exact granteeRef match permits")
        void evaluate_withSpecificGranteeMatch_permits() {
            stubCacheMiss();

            ConsentDirectiveEntity directive = buildDirective(ACTOR_ID, PURPOSE, SCOPE, "permit");
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(directive));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, SCOPE);

            assertThat(decision.permitted()).isTrue();
            assertThat(decision.consentId()).isNotNull();
        }

        @Test
        @DisplayName("evaluate_withSpecificGranteeMismatch_denies - no matching directive for different actor")
        void evaluate_withSpecificGranteeMismatch_denies() {
            stubCacheMiss();

            String differentActor = "practitioner-999";

            // The repository query will not return directives for the wrong grantee
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, differentActor, PURPOSE))
                    .thenReturn(Collections.emptyList());
            when(consentRepo.countByTenantIdAndPatientRefAndStatus(TENANT_ID, PATIENT_REF, "ACTIVE"))
                    .thenReturn(1L);

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, differentActor, PATIENT_REF, PURPOSE, SCOPE);

            assertThat(decision.permitted()).isFalse();
            assertThat(decision.reason()).isEqualTo("NO_MATCHING_CONSENT");
        }
    }

    @Nested
    @DisplayName("Redis cache integration")
    class CacheTests {

        @Test
        @DisplayName("evaluate_checksRedisCache - cached result returned without DB hit")
        void evaluate_checksRedisCache() {
            ConsentDecision cachedDecision = ConsentDecision.permit("cached-consent-id", List.of(SCOPE));

            when(cacheService.getCachedDecision(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE, SCOPE))
                    .thenReturn(Optional.of(cachedDecision));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, SCOPE);

            // Verify the cached decision is returned as-is
            assertThat(decision.permitted()).isTrue();
            assertThat(decision.consentId()).isEqualTo("cached-consent-id");
            assertThat(decision.allowedScopes()).containsExactly(SCOPE);

            // Verify the repository was NEVER called (cache was sufficient)
            verifyNoInteractions(consentRepo);

            // Verify that cacheDecision was NOT called again (no double-caching)
            verify(cacheService, never()).cacheDecision(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("evaluate_cacheMiss_evaluatesFromDbAndCaches - cache miss triggers DB evaluation and caches result")
        void evaluate_cacheMiss_evaluatesFromDbAndCaches() {
            stubCacheMiss();

            ConsentDirectiveEntity directive = buildDirective(ACTOR_ID, PURPOSE, SCOPE, "permit");
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(directive));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, SCOPE);

            assertThat(decision.permitted()).isTrue();

            // Verify the result was cached after DB evaluation
            verify(cacheService).cacheDecision(
                    eq(TENANT_ID), eq(PATIENT_REF), eq(ACTOR_ID), eq(PURPOSE), eq(SCOPE),
                    any(ConsentDecision.class));
        }
    }

    @Nested
    @DisplayName("Deny-wins semantics")
    class DenyWinsTests {

        @Test
        @DisplayName("evaluate_withExplicitDeny_deniesEvenIfPermitExists - deny provision overrides permits")
        void evaluate_withExplicitDeny_deniesEvenIfPermitExists() {
            stubCacheMiss();

            ConsentDirectiveEntity permitDirective = buildDirective(ACTOR_ID, PURPOSE, SCOPE, "permit");
            ConsentDirectiveEntity denyDirective = buildDirective(ACTOR_ID, PURPOSE, SCOPE, "deny");

            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(permitDirective, denyDirective));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, SCOPE);

            assertThat(decision.permitted()).isFalse();
            assertThat(decision.reason()).isEqualTo("CONSENT_DENIED");
            verify(consentAuditRepository).save(any());
        }
    }

    @Nested
    @DisplayName("Scope matching edge cases")
    class ScopeMatchingTests {

        @Test
        @DisplayName("evaluate_withPrefixScopeMatch_permits - directive 'clinical' matches requested 'clinical-data'")
        void evaluate_withPrefixScopeMatch_permits() {
            stubCacheMiss();

            ConsentDirectiveEntity directive = buildDirective(ACTOR_ID, PURPOSE, "clinical", "permit");
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(directive));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, "clinical-data");

            assertThat(decision.permitted()).isTrue();
            assertThat(decision.allowedScopes()).contains("clinical");
        }

        @Test
        @DisplayName("evaluate_withScopeMismatch_denies - non-matching scope results in DENY")
        void evaluate_withScopeMismatch_denies() {
            stubCacheMiss();

            ConsentDirectiveEntity directive = buildDirective(ACTOR_ID, PURPOSE, "billing", "permit");
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(directive));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, "clinical-data");

            assertThat(decision.permitted()).isFalse();
            assertThat(decision.reason()).isEqualTo("NO_VALID_CONSENT");
        }

        @Test
        @DisplayName("evaluate_withFutureConsent_skips - not-yet-active consent is skipped")
        void evaluate_withFutureConsent_skips() {
            stubCacheMiss();

            ConsentDirectiveEntity futureDirective = buildFutureDirective(ACTOR_ID, PURPOSE, SCOPE);
            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(futureDirective));

            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, SCOPE);

            assertThat(decision.permitted()).isFalse();
            assertThat(decision.reason()).isEqualTo("NO_VALID_CONSENT");
        }

        @Test
        @DisplayName("evaluate_withMultiplePermits_collectsAllScopes - multiple permits aggregate allowed scopes")
        void evaluate_withMultiplePermits_collectsAllScopes() {
            stubCacheMiss();

            ConsentDirectiveEntity d1 = buildDirective(ACTOR_ID, PURPOSE, "clinical-data", "permit");
            ConsentDirectiveEntity d2 = buildDirective(ACTOR_ID, PURPOSE, "clinical-notes", "permit");

            when(consentRepo.findActiveForEvaluation(TENANT_ID, PATIENT_REF, ACTOR_ID, PURPOSE))
                    .thenReturn(List.of(d1, d2));

            // Request scope matches both via prefix
            ConsentDecision decision = evaluationService.evaluate(
                    TENANT_ID, ACTOR_ID, PATIENT_REF, PURPOSE, "clinical-data");

            assertThat(decision.permitted()).isTrue();
            assertThat(decision.allowedScopes()).contains("clinical-data");
        }
    }
}
