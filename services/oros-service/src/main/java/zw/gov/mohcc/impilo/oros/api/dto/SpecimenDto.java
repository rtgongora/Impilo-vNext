package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.domain.SpecimenStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.SpecimenEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Summary view of a laboratory specimen. */
public record SpecimenDto(
        UUID specimenId,
        String orderId,
        String specimenType,
        String specimenSource,
        String bodySite,
        String sampleId,
        String labNumber,
        SpecimenStatus status,
        String fastingStatus,
        String collectedBy,
        OffsetDateTime collectedAt,
        String collectionSite,
        OffsetDateTime dispatchedAt,
        OffsetDateTime receivedAt,
        String rejectionReason,
        String chainOfCustody,
        OffsetDateTime createdAt
) {
    public static SpecimenDto from(SpecimenEntity e) {
        return new SpecimenDto(
                e.getSpecimenId(),
                e.getOrderId(),
                e.getSpecimenType(),
                e.getSpecimenSource(),
                e.getBodySite(),
                e.getSampleId(),
                e.getLabNumber(),
                e.getStatus(),
                e.getFastingStatus(),
                e.getCollectedBy(),
                e.getCollectedAt(),
                e.getCollectionSite(),
                e.getDispatchedAt(),
                e.getReceivedAt(),
                e.getRejectionReason(),
                e.getChainOfCustody(),
                e.getCreatedAt()
        );
    }
}
