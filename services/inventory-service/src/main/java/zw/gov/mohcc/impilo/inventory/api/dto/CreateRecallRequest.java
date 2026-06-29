package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import zw.gov.mohcc.impilo.inventory.domain.RecallSeverity;
import zw.gov.mohcc.impilo.inventory.domain.RecallSource;

/**
 * Request to open a recall.
 */
public record CreateRecallRequest(
        String recallReference,
        @NotBlank String itemCode,
        String batchNumber,
        RecallSource source,
        RecallSeverity severity,
        String reason
) {}
