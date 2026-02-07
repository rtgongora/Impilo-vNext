package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.util.UUID;

/**
 * Response carrying a generated CPID (canonical or provisional).
 */
public record CpidResponse(
        UUID cpid,
        boolean provisional
) {}
