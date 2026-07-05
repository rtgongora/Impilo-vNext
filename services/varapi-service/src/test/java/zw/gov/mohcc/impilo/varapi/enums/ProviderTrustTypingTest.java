package zw.gov.mohcc.impilo.varapi.enums;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.varapi.core.collaboration.ExternalProviderCollaborationService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IATG Wave-1 typing invariants: the external 5-level ladder maps losslessly onto the
 * shared {@link ProviderTrustLevel}, channel mapping matches bootstrap provenance, and
 * the V017 backfill mirrors the Java-side mapping exactly.
 */
class ProviderTrustTypingTest {

    // ── External ladder → shared enum ────────────────────────────────────────

    @Test
    void externalLadderMapsOntoSharedTrustEnum() {
        assertThat(ProviderTrustLevel.fromExternalLabel("SELF_ASSERTED"))
                .isEqualTo(ProviderTrustLevel.SELF_ASSERTED);
        assertThat(ProviderTrustLevel.fromExternalLabel("COUNCIL_NUMBER_FORMAT_VALID"))
                .isEqualTo(ProviderTrustLevel.DOCUMENT_SUPPORTED);
        assertThat(ProviderTrustLevel.fromExternalLabel("COUNCIL_NUMBER_FOUND"))
                .isEqualTo(ProviderTrustLevel.DOCUMENT_SUPPORTED);
        assertThat(ProviderTrustLevel.fromExternalLabel("COUNCIL_AND_IDENTITY_MATCH"))
                .isEqualTo(ProviderTrustLevel.COUNCIL_VERIFIED);
        assertThat(ProviderTrustLevel.fromExternalLabel("FULLY_LINKED_OR_ONBOARDED"))
                .isEqualTo(ProviderTrustLevel.FULLY_LINKED_OR_ONBOARDED);
        assertThatThrownBy(() -> ProviderTrustLevel.fromExternalLabel("SOMETHING_ELSE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void collaborationConstantsKeepIdenticalStringValues() {
        // Aliased constants must be byte-identical to the legacy literals so the
        // external profile trust_level column and payloads are untouched.
        assertThat(ExternalProviderCollaborationService.TRUST_SELF_ASSERTED)
                .isEqualTo("SELF_ASSERTED");
        assertThat(ExternalProviderCollaborationService.TRUST_COUNCIL_FORMAT)
                .isEqualTo("COUNCIL_NUMBER_FORMAT_VALID");
        assertThat(ExternalProviderCollaborationService.TRUST_COUNCIL_FOUND)
                .isEqualTo("COUNCIL_NUMBER_FOUND");
        assertThat(ExternalProviderCollaborationService.TRUST_COUNCIL_IDENTITY)
                .isEqualTo("COUNCIL_AND_IDENTITY_MATCH");
        assertThat(ExternalProviderCollaborationService.TRUST_FULLY_LINKED)
                .isEqualTo("FULLY_LINKED_OR_ONBOARDED");
    }

    @Test
    void trustLadderIsOrderedForMonotonicity() {
        assertThat(ProviderTrustLevel.SELF_ASSERTED.canUpgradeTo(ProviderTrustLevel.COUNCIL_VERIFIED)).isTrue();
        assertThat(ProviderTrustLevel.COUNCIL_VERIFIED.canUpgradeTo(ProviderTrustLevel.COUNCIL_VERIFIED)).isTrue();
        assertThat(ProviderTrustLevel.COUNCIL_VERIFIED.canUpgradeTo(ProviderTrustLevel.SELF_ASSERTED)).isFalse();
        assertThat(ProviderTrustLevel.FULLY_LINKED_OR_ONBOARDED.canUpgradeTo(ProviderTrustLevel.EMPLOYMENT_MATCHED)).isFalse();
    }

    // ── Bootstrap origin → channel ───────────────────────────────────────────

    @Test
    void onboardingChannelMapsFromBootstrapOrigin() {
        assertThat(OnboardingChannel.fromBootstrapOrigin("BULK_PRELOAD"))
                .isEqualTo(OnboardingChannel.WORKFORCE_B);
        assertThat(OnboardingChannel.fromBootstrapOrigin("COUNCIL_IMPORT"))
                .isEqualTo(OnboardingChannel.REGULATORY_A);
        assertThat(OnboardingChannel.fromBootstrapOrigin("SELF_REGISTERED"))
                .isEqualTo(OnboardingChannel.SELF);
        assertThat(OnboardingChannel.fromBootstrapOrigin("SOMETHING_ELSE")).isNull();
        assertThat(OnboardingChannel.fromBootstrapOrigin(null)).isNull();
    }

    // ── V017 backfill mirrors the Java mapping ───────────────────────────────

    @Test
    void v017BackfillMirrorsJavaMappings() throws IOException {
        String sql = readMigration("/db/migration/V017__provider_trust_channel_registry_status.sql");

        // trust_level default backfill
        assertThat(sql).contains("SET trust_level = 'SELF_ASSERTED'");

        // channel backfill mirrors OnboardingChannel.fromBootstrapOrigin
        assertThat(sql).contains("WHEN 'BULK_PRELOAD'    THEN 'WORKFORCE_B'");
        assertThat(sql).contains("WHEN 'COUNCIL_IMPORT'  THEN 'REGULATORY_A'");
        assertThat(sql).contains("WHEN 'SELF_REGISTERED' THEN 'SELF'");

        // registry_status backfill only for pre-verification lifecycles
        assertThat(sql).contains("SET registry_status = 'PENDING_VERIFICATION'");
        assertThat(sql).contains("lifecycle_status IN ('PRELOADED', 'UNDER_VERIFICATION')");

        // Additive only: the migration must not drop/rename any of the four legacy axes.
        assertThat(sql).doesNotContainIgnoringCase("DROP COLUMN");
        assertThat(sql).doesNotContainIgnoringCase("RENAME COLUMN");
        assertThat(sql).doesNotContainIgnoringCase("RENAME TO");
    }

    @Test
    void v018CreatesVerificationAttemptTableWithFrozenEvidenceSources() throws IOException {
        String sql = readMigration("/db/migration/V018__provider_verification_attempt.sql");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS varapi.provider_verification_attempt");
        for (String col : new String[]{"provider_id", "evidence_type", "evidence_source",
                "outcome", "confidence", "evidence_ref", "attempted_by", "attempted_at"}) {
            assertThat(sql).contains(col);
        }
        assertThat(sql).contains("COUNCIL_RECORD | HSC_EMPLOYMENT | DOCUMENT | ORG_ATTESTATION");
    }

    private static String readMigration(String classpathLocation) throws IOException {
        try (InputStream in = ProviderTrustTypingTest.class.getResourceAsStream(classpathLocation)) {
            assertThat(in).as("migration %s on classpath", classpathLocation).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
