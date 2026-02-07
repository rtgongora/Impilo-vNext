package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to store an encrypted MOSIP link for a patient.
 *
 * <p>The linkRef is the MOSIP token/handle (opaque). It will be encrypted with
 * AES-256-GCM and hashed with SHA-256 for lookup. The National ID is NEVER
 * part of this request.</p>
 */
public record MosipLinkRequest(
        @NotNull UUID tenantId,
        @NotNull UUID healthId,
        @NotBlank String linkRef,
        String proofingEventRef
) {}
