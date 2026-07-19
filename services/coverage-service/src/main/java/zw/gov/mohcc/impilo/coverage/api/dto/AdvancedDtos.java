package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.coverage.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Wave 4 request/response DTOs (COB §15, employer §24, fraud §25, capitation, gateway §5/§32). */
public final class AdvancedDtos {

    private AdvancedDtos() {}

    // ---- COB ----
    public record CobRequest(@NotBlank String memberCpid, String benefitCode, @NotNull BigDecimal standardCharge) {}

    public record CobView(UUID id, String memberCpid, String benefitCode, BigDecimal standardCharge,
                          BigDecimal allowedAmount, BigDecimal totalPayerPaid, BigDecimal patientResponsibility,
                          String waterfall, String rulesetVersion) {
        public static CobView of(CobDecisionEntity d) {
            return new CobView(d.getId(), d.getMemberCpid(), d.getBenefitCode(), d.getStandardCharge(),
                    d.getAllowedAmount(), d.getTotalPayerPaid(), d.getPatientResponsibility(),
                    d.getWaterfall(), d.getRulesetVersion());
        }
    }

    // ---- Employer / roster ----
    public record RegisterEmployerRequest(@NotBlank String employerCode, @NotBlank String name,
                                          String contractNumber, UUID payerRef) {}

    public record EmployerView(UUID id, String employerCode, String name, String contractNumber, String status) {
        public static EmployerView of(EmployerEntity e) {
            return new EmployerView(e.getId(), e.getEmployerCode(), e.getName(), e.getContractNumber(), e.getStatus());
        }
    }

    public record RosterRowDto(String clientId, String memberNumber, String relationship) {}

    public record StageRosterRequest(@NotNull UUID planId, String uploadedBy, List<RosterRowDto> rows) {}

    public record RosterRowView(UUID id, String clientId, String memberNumber, String relationship,
                                String validationStatus, String validationError, UUID appliedMembershipId) {
        public static RosterRowView of(EmployerRosterRowEntity r) {
            return new RosterRowView(r.getId(), r.getClientId(), r.getMemberNumber(), r.getRelationship(),
                    r.getValidationStatus(), r.getValidationError(), r.getAppliedMembershipId());
        }
    }

    public record RosterBatchView(UUID id, UUID employerId, UUID planId, String status, int totalRows,
                                  int validRows, int invalidRows, int appliedRows) {
        public static RosterBatchView of(EmployerRosterBatchEntity b) {
            return new RosterBatchView(b.getId(), b.getEmployerId(), b.getPlanId(), b.getStatus(),
                    b.getTotalRows(), b.getValidRows(), b.getInvalidRows(), b.getAppliedRows());
        }
    }

    // ---- Fraud ----
    public record ReviewFlagRequest(@NotBlank String status, String reviewerId, String resolution) {}

    public record FraudFlagView(UUID id, String subjectType, String subjectRef, String flagType, String severity,
                                String status, String reviewerId, String resolution, String detail, OffsetDateTime createdAt) {
        public static FraudFlagView of(FraudFlagEntity f) {
            return new FraudFlagView(f.getId(), f.getSubjectType(), f.getSubjectRef(), f.getFlagType(), f.getSeverity(),
                    f.getStatus(), f.getReviewerId(), f.getResolution(), f.getDetail(), f.getCreatedAt());
        }
    }

    // ---- Capitation ----
    public record CapitationRequest(@NotBlank String providerId, String facilityId, String payerId,
                                    @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
                                    Integer memberCount, Integer encounterCount, BigDecimal capitationAmount) {}

    public record CapitationView(UUID id, String providerId, String facilityId, String payerId,
                                 LocalDate periodStart, LocalDate periodEnd, int memberCount, int encounterCount,
                                 BigDecimal capitationAmount, String status) {
        public static CapitationView of(CapitationReportEntity c) {
            return new CapitationView(c.getId(), c.getProviderId(), c.getFacilityId(), c.getPayerId(),
                    c.getPeriodStart(), c.getPeriodEnd(), c.getMemberCount(), c.getEncounterCount(),
                    c.getCapitationAmount(), c.getStatus());
        }
    }

    // ---- Gateway / switch ----
    public record GatewayRequest(@NotBlank String transactionType, String senderRef, String receiverPayer,
                                 String route, String originalReference) {}

    public record GatewayView(UUID id, String switchControlNumber, String transactionType, String senderRef,
                              String receiverPayer, String route, String technicalAck, String businessAck,
                              String status) {
        public static GatewayView of(GatewayTransactionEntity t) {
            return new GatewayView(t.getId(), t.getSwitchControlNumber(), t.getTransactionType(), t.getSenderRef(),
                    t.getReceiverPayer(), t.getRoute(), t.getTechnicalAck(), t.getBusinessAck(), t.getStatus());
        }
    }
}
