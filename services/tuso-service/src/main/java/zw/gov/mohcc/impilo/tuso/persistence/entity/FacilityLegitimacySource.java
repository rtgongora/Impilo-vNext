package zw.gov.mohcc.impilo.tuso.persistence.entity;

/**
 * The distinct authorities whose legitimacy verdict about a facility is tracked <b>separately</b>
 * (honest per-source model — never collapsed into a single boolean).
 *
 * <p>Each source may hold a different, simultaneously-true verdict: a public facility can be
 * Ministry-recognised and operating while its HPA registration is expired. The composite view
 * ({@code FacilitySourceLegitimacyService#getComposite}) surfaces all verdicts side by side
 * instead of pretending one authority speaks for all.</p>
 */
public enum FacilityLegitimacySource {

    /** Health Professions Authority — legal registration/licensure verdict (certificate carrier). */
    HPA_LEGAL,

    /** Ministry of Health and Child Care — operational recognition via the national facility register. */
    MINISTRY_OPERATIONAL,

    /** Impilo platform — platform-side operational verification verdict. */
    PLATFORM_OPERATIONAL
}
