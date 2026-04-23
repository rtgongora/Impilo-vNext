package zw.gov.mohcc.impilo.varapi.api.dto.collaboration;

public record ExternalProviderProfileResponse(
        long id,
        String temporaryProviderPublicId,
        String trustLevel,
        String verificationState,
        boolean councilRegistrationMatched,
        String matchedProviderPublicId,
        String linkedProviderPublicId) {
}
