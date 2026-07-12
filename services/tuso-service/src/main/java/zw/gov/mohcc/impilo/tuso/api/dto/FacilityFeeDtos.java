package zw.gov.mohcc.impilo.tuso.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request/response DTOs for the regulatory fee-schedule + application fee surfaces. */
public final class FacilityFeeDtos {

    private FacilityFeeDtos() {}

    public record FeeScheduleView(UUID feeId, String scheduleCode, int version, String feeCode,
                                  String appliesToCatalogueCode, String appliesToClassCode,
                                  BigDecimal amount, String currency, String sourceInstrument, String sourceSection,
                                  String status, LocalDate effectiveFrom, String approvedBy, Instant approvedAt,
                                  String notes) {}

    public record CreateFeeScheduleRequest(String scheduleCode, String feeCode, String appliesToCatalogueCode,
                                           String appliesToClassCode, BigDecimal amount, String currency,
                                           String sourceInstrument, String sourceSection, Integer version,
                                           String notes) {}

    public record ApproveFeeScheduleRequest(LocalDate effectiveFrom) {}

    /** The fee position of a single application, composed for the applicant/registrar. */
    public record ApplicationFeeView(UUID applicationId, String catalogueCode, boolean feeRequired,
                                     String feeState, String feeCode, BigDecimal amount, String currency,
                                     String sourceInstrument, UUID feeScheduleRef, String feeReference,
                                     String paymentReference, String waivedBy, Instant waivedAt, String waiverReason,
                                     boolean blocksInspection) {}

    public record WaiveFeeRequest(String reason) {}
}
