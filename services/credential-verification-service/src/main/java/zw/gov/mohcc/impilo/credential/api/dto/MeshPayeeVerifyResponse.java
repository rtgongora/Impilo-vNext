package zw.gov.mohcc.impilo.credential.api.dto;

import java.util.UUID;

public record MeshPayeeVerifyResponse(
        UUID verificationRef,
        boolean valid,
        String status,
        UUID credentialId
) {}
