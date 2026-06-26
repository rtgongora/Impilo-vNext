package zw.gov.mohcc.impilo.varapi.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Bulk provider preload request (L3 W6 bootstrap chain). A national-admin or
 * org representative submits a roster of provider skeletons to be created in
 * {@code PRELOADED} state; each yields a single-use claim token for the named
 * provider to later self-claim.
 */
public record BulkPreloadRequest(
        @NotEmpty(message = "rows must not be empty")
        @Valid
        List<PreloadRow> rows
) {
    /**
     * One provider skeleton in a preload roster. Demographics are the minimum
     * an org rep can vouch for; the real provider fills the rest at claim time.
     */
    public record PreloadRow(
            @NotNull(message = "givenName is required") String givenName,
            @NotNull(message = "familyName is required") String familyName,
            String profession,
            String cadre,
            /** Council code (e.g. MDPCZ) the registration number belongs to. */
            String councilCode,
            /** Council/EC registration number — the natural key for de-duplication. */
            String registrationNumber,
            /** Optional delivery hint (masked email/phone) for the claim token; never used to authenticate. */
            String contactHint,
            Long employmentOrgId
    ) {}
}
