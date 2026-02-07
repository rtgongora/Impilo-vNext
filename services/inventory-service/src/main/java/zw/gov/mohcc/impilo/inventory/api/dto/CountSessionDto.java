package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.CountSessionStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CountSessionEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a stock count session with its line items.
 *
 * @param sessionId   the session identifier
 * @param facilityId  the facility identifier
 * @param storeId     the store identifier
 * @param binId       the bin identifier (null for whole-store count)
 * @param status      the current session status
 * @param createdBy   who created the session
 * @param submittedBy who submitted the session for review
 * @param submittedAt when the session was submitted
 * @param approvedBy  who approved the session
 * @param approvedAt  when the session was approved
 * @param notes       session notes (approval/rejection)
 * @param createdAt   creation timestamp
 * @param lines       the count lines within this session
 */
public record CountSessionDto(
        UUID sessionId,
        UUID facilityId,
        UUID storeId,
        UUID binId,
        CountSessionStatus status,
        String createdBy,
        String submittedBy,
        OffsetDateTime submittedAt,
        String approvedBy,
        OffsetDateTime approvedAt,
        String notes,
        OffsetDateTime createdAt,
        List<CountLineDto> lines
) {

    /**
     * Map a CountSessionEntity to a CountSessionDto.
     *
     * @param entity the count session entity
     * @param lines  the DTO-mapped count lines
     * @return the DTO representation
     */
    public static CountSessionDto from(CountSessionEntity entity, List<CountLineDto> lines) {
        return new CountSessionDto(
                entity.getSessionId(),
                entity.getFacilityId(),
                entity.getStoreId(),
                entity.getBinId(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getSubmittedBy(),
                entity.getSubmittedAt(),
                entity.getApprovedBy(),
                entity.getApprovedAt(),
                entity.getNotes(),
                entity.getCreatedAt(),
                lines
        );
    }
}
