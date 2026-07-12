package zw.gov.mohcc.impilo.msikaflow.api.dto;

import java.math.BigDecimal;

/**
 * Read projection of an Rx line's substitution proposal (stored on the line's
 * metadata). {@code status} is PENDING | APPROVED | REJECTED.
 */
public record SubstitutionView(
        String orderId,
        String lineId,
        String originalCode,
        String substituteCode,
        BigDecimal substitutePrice,
        String reason,
        String status,
        String proposedBy,
        String approvedBy,
        String rejectedBy
) {}
