package zw.gov.mohcc.impilo.varapi.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Allow-listed public disclosure for practitioner register verification
 * (gateway public lane — docs/architecture/gateway-public-lane-security-adr.md).
 *
 * <p>Contains ONLY public register facts: display name, profession/cadre,
 * register status, practising-certificate validity window and the registering
 * authority label. Never contact details, national identifiers, date of birth,
 * disciplinary detail or internal identifiers.</p>
 *
 * <p>{@code registerStatus} is the single verification verdict. A miss is
 * represented as {@code NOT_FOUND} with every other field {@code null} —
 * the response shape is identical for hits and misses so the endpoint offers
 * no existence oracle beyond the status itself.</p>
 *
 * @param registerStatus         REGISTERED / SUSPENDED / DEREGISTERED / … / NOT_FOUND
 * @param displayName            public register name (title + given + family)
 * @param profession             registered profession
 * @param cadre                  registered cadre
 * @param registrationNumber     the register's stored registration number
 * @param registeredSince        registration date on the register
 * @param registrationExpiryDate registration expiry (null = no recorded expiry)
 * @param licenceStatus          current practising-certificate/licence status
 * @param licenceValidFrom       practising-certificate validity window start
 * @param licenceValidTo         practising-certificate validity window end
 * @param registeringAuthority   registering authority label (council name)
 */
public record PublicPractitionerVerificationResponse(
        String registerStatus,
        String displayName,
        String profession,
        String cadre,
        String registrationNumber,
        LocalDate registeredSince,
        LocalDate registrationExpiryDate,
        String licenceStatus,
        LocalDate licenceValidFrom,
        LocalDate licenceValidTo,
        String registeringAuthority,
        ExperienceSummary experienceSummary,
        String goodStanding,
        /**
         * HAR W2 — false for a registration Impilo knows only from HPA's institution return. The
         * practitioner's own professional council has not confirmed it, and it asserts nothing
         * about a current practising certificate.
         */
        boolean councilVerified,
        /** Provenance of the register entry, e.g. "HPA institution return, 17 July 2026". */
        String registerSource
) {

    public static final String STATUS_NOT_FOUND = "NOT_FOUND";

    /**
     * Read-only, Rito-sourced patient-experience summary (RW6). VARAPI composes and
     * displays it; it never owns or stores it. Verified experience domains only —
     * {@code source} is always "Rito". Null when Rito is disabled/unavailable.
     */
    public record ExperienceSummary(String source, String reportingPeriod,
                                    int verifiedInteractionTotal, List<ExperienceDomain> domains) {}

    public record ExperienceDomain(String domain, BigDecimal verifiedMeanScore, int verifiedCount) {}

    /** Uniform miss shape — same fields, all null except the status. */
    public static PublicPractitionerVerificationResponse notFound() {
        return new PublicPractitionerVerificationResponse(
                STATUS_NOT_FOUND, null, null, null, null, null, null, null, null, null, null, null, null, false, null);
    }
}
