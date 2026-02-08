package zw.gov.mohcc.impilo.mushex.service;

import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimAttachmentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEventEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing insurance claims through their lifecycle:
 * DRAFT -> SUBMITTED -> RECEIVED -> ADJUDICATED -> PAID.
 */
public interface ClaimService {

    /**
     * Create a new insurance claim in DRAFT status.
     */
    ClaimEntity createClaim(UUID tenantId, UUID facilityId, String billId,
                            String insurerId, String totals);

    /**
     * Retrieve a claim by its ID.
     */
    ClaimEntity getClaim(String claimId);

    /**
     * Get the event history for a claim.
     */
    List<ClaimEventEntity> getClaimEvents(String claimId);

    /**
     * Get all attachments for a claim.
     */
    List<ClaimAttachmentEntity> getClaimAttachments(String claimId);

    /**
     * Submit a DRAFT claim for processing.
     */
    ClaimEntity submitClaim(String claimId);

    /**
     * Record an adjudication decision on a submitted claim.
     */
    ClaimEntity recordAdjudication(String claimId, String decision,
                                   BigDecimal patientResidual, BigDecimal insurerPayable);

    /**
     * Dispute an adjudicated claim.
     */
    ClaimEntity disputeClaim(String claimId, String reason);

    /**
     * Add a document attachment to a claim.
     */
    ClaimAttachmentEntity addAttachment(String claimId, String landelaDocId, String docType);
}
