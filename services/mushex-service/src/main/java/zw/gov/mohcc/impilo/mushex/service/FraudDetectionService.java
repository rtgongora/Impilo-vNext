package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.FraudFlagEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.OpsReviewEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.FraudKind;
import zw.gov.mohcc.impilo.mushex.domain.enums.FraudSeverity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.ReviewStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.FraudFlagRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.OpsReviewRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.PaymentIntentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.RefundRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Fraud, waste, and abuse detection service.
 *
 * Provides heuristic checks for suspicious payment patterns including duplicate
 * payments, repeated refunds, and abnormal transaction frequency. When fraud is
 * detected, a FraudFlagEntity is created along with an OpsReviewEntity for manual
 * investigation.
 */
@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    /** Maximum number of intents per facility per hour before flagging. */
    private static final long ABNORMAL_FREQUENCY_THRESHOLD = 100;

    /** Maximum number of refunds per intent before flagging. */
    private static final long REPEATED_REFUND_THRESHOLD = 3;

    private final FraudFlagRepository fraudFlagRepository;
    private final OpsReviewRepository opsReviewRepository;
    private final PaymentIntentRepository intentRepository;
    private final RefundRepository refundRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FraudDetectionService(FraudFlagRepository fraudFlagRepository,
                                 OpsReviewRepository opsReviewRepository,
                                 PaymentIntentRepository intentRepository,
                                 RefundRepository refundRepository,
                                 EventOutboxRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.fraudFlagRepository = fraudFlagRepository;
        this.opsReviewRepository = opsReviewRepository;
        this.intentRepository = intentRepository;
        this.refundRepository = refundRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Check for duplicate payment: whether a source already has a paid intent.
     * If a duplicate is detected, a fraud flag is created.
     *
     * @param intentId   the current intent being checked
     * @param tenantId   the tenant
     * @param sourceType the payment source type
     * @param sourceId   the payment source ID
     * @return true if a duplicate was detected and flagged
     */
    @Transactional
    public boolean checkDuplicatePayment(String intentId, UUID tenantId, SourceType sourceType, String sourceId) {
        long paidCount = intentRepository.countBySourceTypeAndSourceIdAndStatus(sourceType, sourceId, IntentStatus.PAID);

        if (paidCount > 0) {
            log.warn("Duplicate payment detected: sourceType={}, sourceId={} already has {} paid intent(s)",
                    sourceType, sourceId, paidCount);

            String evidence;
            try {
                evidence = objectMapper.writeValueAsString(Map.of(
                        "intentId", intentId,
                        "sourceType", sourceType.name(),
                        "sourceId", sourceId,
                        "existingPaidCount", paidCount
                ));
            } catch (Exception e) {
                evidence = "{}";
            }

            flagFraud(tenantId, FraudKind.DUPLICATE_PAYMENT, FraudSeverity.HIGH,
                    "PAYMENT_INTENT", intentId, evidence);
            return true;
        }

        return false;
    }

    /**
     * Check for repeated refund attempts on the same intent.
     * Flags if the number of refunds exceeds the threshold.
     *
     * @param intentId the intent to check
     * @param tenantId the tenant
     * @return true if repeated refunds were detected and flagged
     */
    @Transactional
    public boolean checkRepeatedRefund(String intentId, UUID tenantId) {
        long refundCount = refundRepository.countByIntentId(intentId);

        if (refundCount >= REPEATED_REFUND_THRESHOLD) {
            log.warn("Repeated refund detected: intentId={} has {} refund(s)", intentId, refundCount);

            String evidence;
            try {
                evidence = objectMapper.writeValueAsString(Map.of(
                        "intentId", intentId,
                        "refundCount", refundCount,
                        "threshold", REPEATED_REFUND_THRESHOLD
                ));
            } catch (Exception e) {
                evidence = "{}";
            }

            flagFraud(tenantId, FraudKind.REPEATED_REFUND, FraudSeverity.MEDIUM,
                    "PAYMENT_INTENT", intentId, evidence);
            return true;
        }

        return false;
    }

    /**
     * Check for abnormal payment frequency at a facility.
     * Flags if the number of intents in the last hour exceeds the threshold.
     *
     * @param tenantId   the tenant
     * @param facilityId the facility to check
     * @return true if abnormal frequency was detected and flagged
     */
    @Transactional
    public boolean checkAbnormalFrequency(UUID tenantId, UUID facilityId) {
        OffsetDateTime oneHourAgo = OffsetDateTime.now().minusHours(1);
        long recentCount = intentRepository.countByTenantIdAndFacilityIdAndCreatedAtAfter(
                tenantId, facilityId, oneHourAgo);

        if (recentCount > ABNORMAL_FREQUENCY_THRESHOLD) {
            log.warn("Abnormal frequency detected: tenant={}, facility={}, count={} in last hour",
                    tenantId, facilityId, recentCount);

            String evidence;
            try {
                evidence = objectMapper.writeValueAsString(Map.of(
                        "facilityId", facilityId.toString(),
                        "recentCount", recentCount,
                        "threshold", ABNORMAL_FREQUENCY_THRESHOLD,
                        "windowHours", 1
                ));
            } catch (Exception e) {
                evidence = "{}";
            }

            flagFraud(tenantId, FraudKind.ABNORMAL_FREQUENCY, FraudSeverity.HIGH,
                    "FACILITY", facilityId.toString(), evidence);
            return true;
        }

        return false;
    }

    /**
     * Create a fraud flag and associated ops review entry.
     *
     * @param tenantId   the tenant
     * @param kind       the type of fraud detected
     * @param severity   the severity level
     * @param entityType the type of entity being flagged (e.g. "PAYMENT_INTENT", "FACILITY")
     * @param entityId   the ID of the flagged entity
     * @param evidence   JSON evidence supporting the flag
     * @return the created fraud flag entity
     */
    @Transactional
    public FraudFlagEntity flagFraud(UUID tenantId, FraudKind kind, FraudSeverity severity,
                                     String entityType, String entityId, String evidence) {
        FraudFlagEntity flag = new FraudFlagEntity();
        flag.setId(UlidGenerator.generate());
        flag.setTenantId(tenantId);
        flag.setKind(kind);
        flag.setSeverity(severity);
        flag.setEntityType(entityType);
        flag.setEntityId(entityId);
        flag.setEvidence(evidence);
        flag.setStatus("OPEN");

        flag = fraudFlagRepository.save(flag);

        // Create an ops review entry for manual investigation
        OpsReviewEntity review = new OpsReviewEntity();
        review.setId(UlidGenerator.generate());
        review.setTenantId(tenantId);
        review.setQueueType("FRAUD");
        review.setEntityType(entityType);
        review.setEntityId(entityId);
        review.setStatus(ReviewStatus.PENDING);

        opsReviewRepository.save(review);

        log.info("Fraud flagged: kind={}, severity={}, entity={}/{}, flagId={}",
                kind, severity, entityType, entityId, flag.getId());

        publishEvent("FRAUD", flag.getId(), "FRAUD_FLAGGED",
                Map.of(
                        "flagId", flag.getId(),
                        "kind", kind.name(),
                        "severity", severity.name(),
                        "entityType", entityType,
                        "entityId", entityId
                ),
                tenantId);

        return flag;
    }

    /**
     * List fraud flags for a tenant with pagination.
     *
     * @param tenantId the tenant ID
     * @param pageable pagination parameters
     * @return page of fraud flags
     */
    public Page<FraudFlagEntity> getFlags(UUID tenantId, Pageable pageable) {
        return fraudFlagRepository.findByTenantId(tenantId, pageable);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, Map<String, Object> payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
