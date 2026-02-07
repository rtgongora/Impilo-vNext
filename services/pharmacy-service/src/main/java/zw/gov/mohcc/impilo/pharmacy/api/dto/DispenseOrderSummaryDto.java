package zw.gov.mohcc.impilo.pharmacy.api.dto;

import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary DTO for dispense order listings and API responses.
 * Uses a static factory method to map from the entity.
 */
public record DispenseOrderSummaryDto(
        UUID orderId,
        UUID tenantId,
        UUID facilityId,
        String patientCpid,
        String orosOrderId,
        DispenseStatus status,
        OrderPriority priority,
        String prescriberNotes,
        String acceptedBy,
        OffsetDateTime acceptedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /**
     * Factory method to create a summary DTO from a dispense order entity.
     */
    public static DispenseOrderSummaryDto from(DispenseOrderEntity entity) {
        return new DispenseOrderSummaryDto(
                entity.getOrderId(),
                entity.getTenantId(),
                entity.getFacilityId(),
                entity.getPatientCpid(),
                entity.getOrosOrderId(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getPrescriberNotes(),
                entity.getAcceptedBy(),
                entity.getAcceptedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
