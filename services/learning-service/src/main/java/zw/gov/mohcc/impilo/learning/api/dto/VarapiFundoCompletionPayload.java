package zw.gov.mohcc.impilo.learning.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload forwarded from Varapi after Fundo webhook ingestion (governed CPD candidate created).
 * Learning layer records completion/progress separately from CPD credit acceptance.
 */
public record VarapiFundoCompletionPayload(
        UUID tenantId,
        String providerPublicId,
        Long councilId,
        String courseId,
        String courseName,
        Instant completedAt,
        int creditsSuggested,
        String externalRef,
        Long varapiFundoCandidateId) {}
