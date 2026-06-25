package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

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
        ImagingWorkflowState imagingState
) {
    /**
     * Factory method to create a summary DTO from an order entity.
     */
    public static OrderSummaryDto from(OrderEntity entity) {
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
                entity.getImagingState()
        );
    }
}
