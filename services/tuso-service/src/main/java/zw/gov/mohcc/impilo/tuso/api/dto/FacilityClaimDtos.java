package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the facility administrator claim/appointment lane (IATG Wave 2, WS-E).
 *
 * <p>Internal (service-plane) contract consumed by the experience-BFF facility-claim orchestration.</p>
 */
public final class FacilityClaimDtos {

    private FacilityClaimDtos() {}

    /**
     * Whether a facility can be claimed/administered right now.
     *
     * <p>{@code claimable} = the facility is allowed on the platform (per its
     * FacilitySourceLegitimacy verdicts — no denying source and at least one allowing source).
     * {@code alreadyAdministered} reports whether at least one ACTIVE administrator already exists
     * (a facility may still accept additional administrators).</p>
     */
    public record EligibilityView(
            UUID facilityUuid,
            boolean allowedOnPlatform,
            boolean claimable,
            boolean alreadyAdministered,
            int activeAdministratorCount,
            List<String> reasons) {
    }

    /** Non-sensitive facility summary shown before an administrator commits to a claim. */
    public record PreviewView(
            UUID facilityUuid,
            String facilityCode,
            String name,
            String facilityType,
            String province,
            String district,
            boolean allowedOnPlatform,
            boolean claimable) {
    }

    /**
     * Submit-claim body. The claimant ({@code personHealthId}) is supplied by the trusted
     * caller (BFF), bound to the session actor. {@code evidenceRef} (proof of authority)
     * is mandatory since W4 (D-L4). {@code claimType}: NEW | RECOVERY (FJ8).
     */
    public record SubmitClaimRequest(
            @NotBlank String personHealthId,
            String role,
            String evidenceRef,
            String claimType,
            LocalDate validFrom,
            LocalDate validTo,
            String notes,
            /**
             * What the claimant says they are to this facility (FCV-W4). Regulated relationships —
             * practitioner in charge, responsible pharmacist and the like — additionally require a
             * professional identifier that resolves; administrative ones must not.
             */
            String relationshipType,
            /** Professional identifier for a regulated relationship: PROVIDER_ID or COUNCIL_REG. */
            String professionalIdKind,
            String professionalIdValue,
            String councilCode,
            /** Required when the relationship is "other" — say what it is. */
            String justification) {
    }

    /** Administrator approval body (who approves; provenance only). */
    public record ApproveAppointmentRequest(String approvedBy) {
    }

    /**
     * The steward's reason for refusing or withdrawing an appointment. Required — a claimant who is
     * turned down is entitled to know why, and the steward's judgement is the record.
     */
    public record DecisionReasonRequest(String reason) {
    }

    /** A facility administrator appointment as returned to the service plane. */
    public record AppointmentView(
            Long id,
            UUID facilityUuid,
            String personHealthId,
            String role,
            String appointedBy,
            String approvalState,
            String evidenceRef,
            LocalDate validFrom,
            LocalDate validTo,
            Instant createdAt,
            Instant updatedAt) {
    }
}
