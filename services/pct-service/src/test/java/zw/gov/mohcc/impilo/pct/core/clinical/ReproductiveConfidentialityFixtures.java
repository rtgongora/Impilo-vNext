package zw.gov.mohcc.impilo.pct.core.clinical;

import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialCarePolicy;
import zw.gov.mohcc.impilo.reproductive.confidentiality.ConfidentialityCategory;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Confidentiality test doubles shared by the reproductive service tests.
 *
 * <p>Deliberately offers a {@link #unresolvedPolicy()} as the default: a test that does not care
 * about confidentiality should exercise the degraded path, because that is what a service sees when
 * CKP is unreachable and it must still accept the write. A fixture that quietly supplied a working
 * age would let a stamp-rejects-the-write regression pass every existing test.
 */
final class ReproductiveConfidentialityFixtures {

    static final String SRH = "SEXUAL_REPRODUCTIVE_HEALTH";

    private ReproductiveConfidentialityFixtures() {
    }

    /** CKP unreachable: category still stamped, class left FULL_CLINICAL, basis POLICY_UNAVAILABLE. */
    static ConfidentialCarePolicyProvider unresolvedPolicy() {
        ConfidentialCarePolicyProvider provider = mock(ConfidentialCarePolicyProvider.class);
        when(provider.policyAsOf(any(), any())).thenReturn(null);
        when(provider.subjectAt(any(), any(), any(), any()))
                .thenReturn(new ConfidentialityStamper.SubjectContext(null, false));
        when(provider.classStampingEnabled()).thenReturn(false);
        return provider;
    }

    /**
     * A governed policy in force, with the subject's date of birth resolvable.
     *
     * @param classStampingEnabled the post-flip switch — true only in tests that prove the flipped
     *                             behaviour, never in production configuration
     */
    static ConfidentialCarePolicyProvider policy(int age, LocalDate dateOfBirth,
                                                 boolean classStampingEnabled) {
        ConfidentialCarePolicyProvider provider = mock(ConfidentialCarePolicyProvider.class);
        when(provider.policyAsOf(any(), any())).thenReturn(new ConfidentialCarePolicy() {
            @Override
            public Optional<Integer> confidentialFromGuardianAgeYears(ConfidentialityCategory c) {
                return c == ConfidentialityCategory.SEXUAL_REPRODUCTIVE_HEALTH
                        ? Optional.of(age) : Optional.empty();
            }

            @Override
            public Optional<Boolean> nonTerminationLossConfidentialFromGuardian() {
                return Optional.of(true);
            }

            @Override
            public String contentVersion() {
                return "test-policy-1.0.0";
            }

            @Override
            public boolean ratified() {
                return false;
            }
        });
        when(provider.subjectAt(any(), any(), any(), any()))
                .thenReturn(new ConfidentialityStamper.SubjectContext(dateOfBirth, false));
        when(provider.classStampingEnabled()).thenReturn(classStampingEnabled);
        return provider;
    }

    /** A requester the PDP has granted the named confidential categories. */
    static VisibilityProfile profileGranting(String... categories) {
        VisibilityProfile.Builder builder = VisibilityProfile.builder(VisibilityProfile.legacyUnrestricted());
        builder.grantConfidentialCategories(List.of(categories));
        return builder.build();
    }
}
