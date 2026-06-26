package zw.gov.mohcc.impilo.varapi.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Self-claim of a preloaded provider profile (L3 W6). The claimant has already
 * authenticated as a person (person-first login); they present the claim token
 * delivered to them and bind their verified Impilo Health ID to the skeleton.
 */
public record ClaimProfileRequest(
        @NotNull(message = "claimToken is required") String claimToken,
        /**
         * The authenticated claimant's Impilo Health ID. The controller cross-checks
         * this against the trust context, so a token cannot be redeemed onto an
         * arbitrary anchor.
         */
        @NotNull(message = "claimantHealthId is required") UUID claimantHealthId
) {}
