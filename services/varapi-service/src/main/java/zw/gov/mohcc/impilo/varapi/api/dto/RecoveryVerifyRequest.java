package zw.gov.mohcc.impilo.varapi.api.dto;

public record RecoveryVerifyRequest(
        String providerPublicId,
        String biometricRef
) {}
