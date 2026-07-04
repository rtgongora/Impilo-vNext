package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Request to start a new patient journey at a facility.
 *
 * <p>A journey represents a single patient visit through a facility,
 * from arrival through triage, service delivery, and departure
 * (discharge, transfer, or death recording).</p>
 *
 * @param patientCpid  the patient's Common Patient Identifier (CPID)
 * @param facilityId   the facility where the journey begins
 * @param referralSource the source of referral, if applicable (e.g. OPD, EMERGENCY, REFERRAL)
 * @param referralId   the referral document identifier, if applicable
 * @param appointmentId the booking appointment that produced this journey,
 *                      if the visit was scheduled (distinguishes scheduled
 *                      check-ins from walk-ins)
 */
public record StartJourneyRequest(
        @NotBlank String patientCpid,
        UUID facilityId,
        String referralSource,
        String referralId,
        UUID appointmentId
) {}
