package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.SpecimenEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary DTO for order listings and API responses.
 * Uses a static factory method to map from the entity.
 *
 * <p>Surfaces the diagnostic/imaging journey fields (request source, accession number,
 * referring provider, scheduled acquisition time, fine-grained imaging state) alongside the
 * coarse canonical {@link OrderStatus}.</p>
 */
public record OrderSummaryDto(
        String orderId,
        OrderType orderType,
        OrderStatus status,
        OrderPriority priority,
        String patientCpid,
        UUID facilityId,
        OffsetDateTime placedAt,
        String placedBy,
        String encounterRef,
        String ziboOrderCode,
        String clinicalNotes,
        OffsetDateTime updatedAt,
        // ── Diagnostic/imaging journey ──
        RequestSource requestSource,
        String accessionNumber,
        String referringProviderId,
        String referringProviderName,
        OffsetDateTime scheduledAt,
        ImagingWorkflowState imagingState,
        String workflowState,
        String studyUid,
        String studyViewerUrl,
        // ── What was ordered, and where it got to ──
        // The EHR results and orders tabs have always declared these. They were never projected,
        // so the columns rendered undefined: a clinician looking at "Results" could not see which
        // test was ordered, whether a specimen had been taken, or whether a result had come back.
        // The data was in oros all along — order_items, specimens, results — just never joined.
        String testName,
        String testCode,
        OffsetDateTime collectedAt,
        OffsetDateTime resultedAt,
        boolean criticalResult,
        String resultStatus
) {
    /**
     * Factory for callers that do not need the ordered-item, specimen or result projection.
     *
     * <p>Prefer {@link #from(OrderEntity, java.util.List, java.util.List, ResultEntity)} on any
     * list a clinician reads: without it {@code testName} and {@code resultedAt} come back null,
     * and a null result timestamp on a results screen reads as "no result yet".
     */
    public static OrderSummaryDto from(OrderEntity entity) {
        return from(entity, java.util.List.of(), java.util.List.of(), null);
    }

    /**
     * Full projection: what was ordered, when the specimen was taken, and whether a result exists.
     *
     * @param items     the order's line items — their display names are the test names
     * @param specimens the order's specimens; the earliest collection is the one a clinician means
     *                  by "collected"
     * @param latest    the most recent result version, or null if nothing has been reported
     */
    public static OrderSummaryDto from(OrderEntity entity,
                                       java.util.List<OrderItemEntity> items,
                                       java.util.List<SpecimenEntity> specimens,
                                       ResultEntity latest) {
        // Multi-test orders (a "renal profile") are one order with several items. Naming only the
        // first would hide the rest, so they are joined — the clinician sees everything ordered.
        String testName = items.stream()
                .map(OrderItemEntity::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(null);
        String testCode = items.stream()
                .map(OrderItemEntity::getCode)
                .filter(code -> code != null && !code.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse(entity.getZiboOrderCode());
        OffsetDateTime collectedAt = specimens.stream()
                .map(SpecimenEntity::getCollectedAt)
                .filter(java.util.Objects::nonNull)
                .min(OffsetDateTime::compareTo)
                .orElse(null);

        return withProjection(entity, testName, testCode, collectedAt,
                latest == null ? null : latest.getReleasedAt(),
                latest != null && latest.isCritical(),
                latest == null || latest.getReportStatus() == null
                        ? null : latest.getReportStatus().name());
    }

    private static OrderSummaryDto withProjection(OrderEntity entity, String testName, String testCode,
                                                  OffsetDateTime collectedAt, OffsetDateTime resultedAt,
                                                  boolean criticalResult, String resultStatus) {
        return new OrderSummaryDto(
                entity.getOrderId(),
                entity.getOrderType(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getPatientCpid(),
                entity.getFacilityId(),
                entity.getPlacedAt(),
                entity.getPlacedBy(),
                entity.getEncounterRef(),
                entity.getZiboOrderCode(),
                entity.getClinicalNotes(),
                entity.getUpdatedAt(),
                entity.getRequestSource(),
                entity.getAccessionNumber(),
                entity.getReferringProviderId(),
                entity.getReferringProviderName(),
                entity.getScheduledAt(),
                entity.getImagingState(),
                entity.getWorkflowState(),
                entity.getStudyUid(),
                entity.getStudyViewerUrl(),
                testName,
                testCode,
                collectedAt,
                resultedAt,
                criticalResult,
                resultStatus
        );
    }
}
