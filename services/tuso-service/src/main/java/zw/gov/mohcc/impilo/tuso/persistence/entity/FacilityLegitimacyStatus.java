package zw.gov.mohcc.impilo.tuso.persistence.entity;

/**
 * Per-source legitimacy verdict for a facility (see {@link FacilityLegitimacySource}).
 *
 * <p>Distinct from {@link FacilityRegulatoryStatus} (the facility's overall HPA lifecycle state):
 * this enum records what a <b>single source</b> currently says, honestly, including "we could not
 * find it" ({@link #NOT_FOUND}) and the governed doctrine escape hatch
 * ({@link #GOVERNMENT_OPERATIONAL_EXCEPTION}) for public facilities that legitimately operate
 * despite a lapsed/absent legal registration — which always requires an explicit reason and an
 * explicit platform-access decision.</p>
 */
public enum FacilityLegitimacyStatus {

    /** The source confirms the facility is currently registered/recognised. */
    REGISTERED_CURRENT,

    /** The source's registration/recognition exists but has lapsed. */
    EXPIRED,

    /** The source has actively suspended the facility. */
    SUSPENDED,

    /** The source has affirmatively flagged the facility as non-compliant. */
    KNOWN_NOT_COMPLIANT,

    /** Verification against the source has not completed yet — verdict honestly unknown. */
    PENDING_VERIFICATION,

    /** The source was consulted and does not know the facility. */
    NOT_FOUND,

    /**
     * Governed exception: a government/public facility operates under Ministry authority even though
     * this source's verdict would otherwise block it (e.g. HPA registration expired). Requires a
     * non-blank reason and an explicit {@code allowedOnPlatform} decision — never implied.
     */
    GOVERNMENT_OPERATIONAL_EXCEPTION
}
