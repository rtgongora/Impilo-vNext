package zw.gov.mohcc.impilo.nhume.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record ProofRequest(
        @JsonProperty("proof_stage") String proofStage,
        @NotBlank @JsonProperty("proof_method") String proofMethod,
        @JsonProperty("captured_by") String capturedBy,
        @JsonProperty("otp_code") String otpCode,
        @JsonProperty("signature_uri") String signatureUri,
        @JsonProperty("photo_uri") String photoUri,
        @JsonProperty("biometric_ref") String biometricRef,
        @JsonProperty("geofence_match") Boolean geofenceMatch,
        @JsonProperty("locker_code") String lockerCode,
        @JsonProperty("webhook_ref") String webhookRef,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("mark_delivered") Boolean markDelivered
) {}
