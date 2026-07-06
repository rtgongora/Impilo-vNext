package zw.gov.mohcc.impilo.varapi.persistence.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave-2 status-axis consolidation invariant: the derived axes
 * ({@code status}, {@code active_flag}, {@code licence_status}) are ALWAYS a
 * deterministic projection of the canonical {@code lifecycle_status}, and
 * {@link ProviderEntity#isActive()} reads lifecycle only. Exercised across a
 * realistic preload → claim → suspend → activate → revoke → retire sequence.
 */
class ProviderStatusProjectionTest {

    /** The derived columns must equal a fresh projection of the same lifecycle. */
    private void assertDerivedConsistent(ProviderEntity p) {
        ProviderEntity reference = new ProviderEntity();
        reference.setLifecycleStatus(p.getLifecycleStatus());
        reference.deriveStatusProjections();

        assertThat(p.getStatus())
                .as("status projection for lifecycle=%s", p.getLifecycleStatus())
                .isEqualTo(reference.getStatus());
        assertThat(p.getActiveFlag())
                .as("active_flag projection for lifecycle=%s", p.getLifecycleStatus())
                .isEqualTo(reference.getActiveFlag());
        assertThat(p.getLicenceStatus())
                .as("licence_status projection for lifecycle=%s", p.getLifecycleStatus())
                .isEqualTo(reference.getLicenceStatus());
        // isActive() is derived from lifecycle ONLY and must agree with the projected active_flag.
        assertThat(p.isActive())
                .as("isActive() for lifecycle=%s", p.getLifecycleStatus())
                .isEqualTo(reference.getActiveFlag());
    }

    private ProviderEntity moveLifecycle(ProviderEntity p, String lifecycle) {
        p.setLifecycleStatus(lifecycle);
        p.deriveStatusProjections();
        assertDerivedConsistent(p);
        return p;
    }

    @Test
    void derivedAxesTrackLifecycleAcrossFullSequence() {
        ProviderEntity p = new ProviderEntity();

        // Preload — non-operational skeleton.
        moveLifecycle(p, "PRELOADED");
        assertThat(p.getStatus()).isEqualTo("INACTIVE");
        assertThat(p.getActiveFlag()).isFalse();
        assertThat(p.getLicenceStatus()).isNull();
        assertThat(p.isActive()).isFalse();

        // Claim — becomes operational.
        moveLifecycle(p, "CLAIMED");
        assertThat(p.getStatus()).isEqualTo("ACTIVE");
        assertThat(p.getActiveFlag()).isTrue();
        assertThat(p.isActive()).isTrue();

        // Suspend.
        moveLifecycle(p, "SUSPENDED");
        assertThat(p.getStatus()).isEqualTo("SUSPENDED");
        assertThat(p.getActiveFlag()).isFalse();
        assertThat(p.getLicenceStatus()).isEqualTo("SUSPENDED");
        assertThat(p.isActive()).isFalse();

        // Re-activate (licenced active).
        moveLifecycle(p, "LICENCED_ACTIVE");
        assertThat(p.getStatus()).isEqualTo("ACTIVE");
        assertThat(p.getActiveFlag()).isTrue();
        assertThat(p.getLicenceStatus()).isEqualTo("ACTIVE");
        assertThat(p.isActive()).isTrue();

        // Revoke / remove.
        moveLifecycle(p, "REMOVED");
        assertThat(p.getStatus()).isEqualTo("REVOKED");
        assertThat(p.getActiveFlag()).isFalse();
        assertThat(p.isActive()).isFalse();

        // Retire.
        moveLifecycle(p, "RETIRED");
        assertThat(p.getStatus()).isEqualTo("INACTIVE");
        assertThat(p.getActiveFlag()).isFalse();
        assertThat(p.isActive()).isFalse();
    }

    @Test
    void statusChangeVocabularyMapsToCanonicalLifecycleThatProjectsBack() {
        // The legacy status-change vocabulary must round-trip: the mapped lifecycle
        // value must project back to exactly the requested status string.
        assertRoundTrip("ACTIVE");
        assertRoundTrip("SUSPENDED");
        assertRoundTrip("INACTIVE");
        assertRoundTrip("REVOKED");
    }

    private void assertRoundTrip(String requestedStatus) {
        ProviderEntity p = new ProviderEntity();
        p.setLifecycleStatus(ProviderEntity.lifecycleForStatusChange(requestedStatus));
        p.deriveStatusProjections();
        assertThat(p.getStatus())
                .as("round-trip for status-change target %s", requestedStatus)
                .isEqualTo(requestedStatus);
        assertDerivedConsistent(p);
    }

    @Test
    void isActiveReadsLifecycleOnly_ignoringStaleDerivedColumns() {
        // Even if the derived columns are (illegally) stale, isActive() consults
        // lifecycle only — proving lifecycle is the single source of operational truth.
        ProviderEntity p = new ProviderEntity();
        p.setLifecycleStatus("SUSPENDED");
        p.setStatus("ACTIVE");       // stale / divergent
        p.setActiveFlag(true);       // stale / divergent
        assertThat(p.isActive()).isFalse();
    }

    @Test
    void unknownLifecycleProjectsToSafeNonOperationalDefault() {
        ProviderEntity p = new ProviderEntity();
        p.setLifecycleStatus("SOME_LEGACY_VALUE_NOT_IN_ENUM");
        p.deriveStatusProjections();
        assertThat(p.getStatus()).isEqualTo("INACTIVE");
        assertThat(p.getActiveFlag()).isFalse();
        assertThat(p.isActive()).isFalse();
    }
}
