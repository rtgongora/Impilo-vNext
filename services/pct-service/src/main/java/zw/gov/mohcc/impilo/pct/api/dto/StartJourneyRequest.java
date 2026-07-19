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
 * @param biometricSubjectRef the subject reference (e.g. CPID) whose enrolled
 *                      biometric template the arrival probe is verified against;
 *                      when supplied alongside {@code biometricProbeBase64} the
 *                      check-in is verified through the shared ABIS seam
 * @param biometricModality the biometric modality of the probe
 *                      ({@code FINGERPRINT}|{@code FACE}|{@code IRIS});
 *                      defaults to {@code FINGERPRINT} when a probe is present
 * @param biometricProbeBase64 the base64-encoded biometric probe template
 *                      captured at the desk; when {@code null}/blank the check-in
 *                      proceeds exactly as before (no biometric binding)
 */
public record StartJourneyRequest(
        @NotBlank String patientCpid,
        UUID facilityId,
        String referralSource,
        String referralId,
        UUID appointmentId,
        String biometricSubjectRef,
        String biometricModality,
        String biometricProbeBase64
) {}
