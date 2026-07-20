package zw.gov.mohcc.impilo.ubomi.integration.rg;

/**
 * The <b>minimal</b> result of an outbound RG verification, as returned by
 * {@link RegistrarGeneralClient} and consumed by {@link RgVerificationService}.
 *
 * <p>Response minimisation is enforced by <em>this DTO's shape</em>, not by
 * discipline downstream: the only fields that can cross the RG boundary are
 * whether there was a match, an opaque {@code civilIdentityToken}, and the
 * narrow set of verified attributes actually needed for the assurance decision.
 * The full RG record (National ID, names, address, parents, ...) is never
 * represented here, so it can never be persisted or emitted.</p>
 *
 * @param matched            whether the RG authoritatively matched the identifier
 * @param civilIdentityToken opaque token the RG issues for a match (never civil PII); null on no-match
 * @param verifiedFullName   whether the RG confirmed the supplied name matched (boolean, not the name itself)
 * @param verifiedDateOfBirth whether the RG confirmed the supplied DOB matched (boolean, not the DOB itself)
 */
public record RgVerificationOutcome(
        boolean matched,
        String civilIdentityToken,
        boolean verifiedFullName,
        boolean verifiedDateOfBirth
) {
    public static RgVerificationOutcome noMatch() {
        return new RgVerificationOutcome(false, null, false, false);
    }
}
