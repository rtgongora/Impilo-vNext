package zw.gov.mohcc.impilo.varapi.core.collaboration;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Channel A — LIVE council / HPA regulatory verification ADAPTER SEAM (IATG Wave 2).
 *
 * <p>Wave 1 ingests council data only via manual bulk upload into the local imported
 * registry ({@code varapi.provider_council_registration_record}). This interface is the
 * <em>seam</em> for an OPTIONAL live authority call that sits <b>in front of</b> that
 * local path. It is honestly gated OFF by default (see
 * {@code varapi.council-regulatory.live-adapter-enabled}); when no live adapter bean is
 * present, or no enabled registration exists for a council, the existing local-registry
 * path runs UNCHANGED.</p>
 *
 * <p><b>No fabrication contract:</b> an implementation MUST return a
 * {@link CouncilVerificationOutcome} that reflects only what the authority actually
 * answered. On any failure to reach or parse the authority it MUST return
 * {@link Result#UNREACHABLE} (an honest degraded outcome) — never a fabricated match or
 * PASS. When there is no live authority configured for the council it MUST return
 * {@link Result#NOT_CONFIGURED} so the caller falls through silently.</p>
 */
public interface CouncilRegistryAdapter {

    /** Canonical evidence source label for attempts produced by the live adapter path. */
    String SOURCE_COUNCIL_LIVE = "COUNCIL_LIVE";

    /**
     * The person/tenant context in which a registration number is being verified. Carries
     * the tenant so the adapter can resolve the tenant-scoped {@code CouncilAdapterRegistrationEntity};
     * names are matching inputs only (never authentication).
     */
    record PersonContext(UUID tenantId, String givenName, String familyName) {}

    /**
     * What the live authority actually answered. {@code IDENTITY_MATCH}/{@code REGISTRY_MATCH}
     * are the only definitive positive results; everything else falls through to the local path.
     */
    enum Result {
        /** Authority confirmed the registration AND the supplied identity aligned. */
        IDENTITY_MATCH,
        /** Authority confirmed the registration number exists (identity not confirmed). */
        REGISTRY_MATCH,
        /** Authority responded but returned no matching registration. */
        NO_MATCH,
        /** The registration number did not satisfy the council number format. */
        FORMAT_INVALID,
        /** Authority could not be reached / response unusable — honest degraded, never a PASS. */
        UNREACHABLE,
        /** No live adapter registration is configured/enabled for this council — fall through. */
        NOT_CONFIGURED
    }

    /**
     * Outcome of a live verification. {@code confidence} is the AUTHORITY's graded score
     * (or {@code null} when no authoritative answer exists); it is never synthesised by the
     * platform. {@code matchedRef} is matching evidence (e.g. an authority record id), never
     * identity, and is masked at API boundaries.
     */
    record CouncilVerificationOutcome(Result result,
                                      BigDecimal confidence,
                                      String sourceType,
                                      String matchedRef,
                                      boolean reachable) {

        public boolean isDefinitiveMatch() {
            return result == Result.IDENTITY_MATCH || result == Result.REGISTRY_MATCH;
        }

        public static CouncilVerificationOutcome identityMatch(BigDecimal confidence, String matchedRef) {
            return new CouncilVerificationOutcome(Result.IDENTITY_MATCH, confidence, SOURCE_COUNCIL_LIVE, matchedRef, true);
        }

        public static CouncilVerificationOutcome registryMatch(BigDecimal confidence, String matchedRef) {
            return new CouncilVerificationOutcome(Result.REGISTRY_MATCH, confidence, SOURCE_COUNCIL_LIVE, matchedRef, true);
        }

        public static CouncilVerificationOutcome noMatch() {
            return new CouncilVerificationOutcome(Result.NO_MATCH, null, SOURCE_COUNCIL_LIVE, null, true);
        }

        public static CouncilVerificationOutcome formatInvalid() {
            return new CouncilVerificationOutcome(Result.FORMAT_INVALID, null, SOURCE_COUNCIL_LIVE, null, true);
        }

        /** Honest degraded outcome — the authority was not reachable. NEVER a PASS. */
        public static CouncilVerificationOutcome unreachable() {
            return new CouncilVerificationOutcome(Result.UNREACHABLE, null, SOURCE_COUNCIL_LIVE, null, false);
        }

        /** No live authority configured for this council — caller falls through silently. */
        public static CouncilVerificationOutcome notConfigured() {
            return new CouncilVerificationOutcome(Result.NOT_CONFIGURED, null, SOURCE_COUNCIL_LIVE, null, false);
        }
    }

    /**
     * Verify a council registration number against the live authority.
     *
     * @param councilCode        the council code (e.g. {@code MDPCZ}), used to resolve the registration
     * @param registrationNumber the number to verify (matching evidence, not identity)
     * @param personContext      tenant + person matching inputs
     * @return an honest outcome — never a fabricated match
     */
    CouncilVerificationOutcome verify(String councilCode, String registrationNumber, PersonContext personContext);
}
