package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record ProofRequest(
        @JsonProperty("proof_stage") @JsonAlias({"stage"}) String proofStage,
        // Web and mobile clients historically send "method"; accept both aliases
        // so proof capture (the collection / drop-off sign-off) binds instead of
        // failing @NotBlank validation as a silent 400.
        @NotBlank @JsonProperty("proof_method") @JsonAlias({"method"}) String proofMethod,
        @JsonProperty("captured_by") String capturedBy,
        @JsonProperty("otp_code") String otpCode,
        @JsonProperty("signature_uri") @JsonAlias({"evidence_ref"}) String signatureUri,
        @JsonProperty("photo_uri") String photoUri,
        @JsonProperty("biometric_ref") String biometricRef,
        @JsonProperty("geofence_match") Boolean geofenceMatch,
        @JsonProperty("locker_code") String lockerCode,
        @JsonProperty("webhook_ref") String webhookRef,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("mark_delivered") Boolean markDelivered,
        // Optional live biometric recipient-verification at handover. When a probe is
        // supplied, the recipient's identity is verified through the shared seam before the
        // handover proof is accepted: MATCH → proof marked biometric-verified; NO_MATCH →
        // handover rejected; UNAVAILABLE → fall back to the declared proof method (unchanged).
        @JsonProperty("biometric_subject_ref") String biometricSubjectRef,
        @JsonProperty("biometric_modality") String biometricModality,
        @JsonProperty("biometric_probe_base64") String biometricProbeBase64,
        // OF-B18 §12.4 — handover attestation artifacts feeding the grade ladder:
        // the receiver's name (named-recipient check) and the courier's ID-check
        // attestation (type + document ref) for ID_CHECK / OTP_PLUS_ID grades.
        @JsonProperty("receiver_name") String receiverName,
        @JsonProperty("id_document_type") String idDocumentType,
        @JsonProperty("id_document_ref") String idDocumentRef
) {}
