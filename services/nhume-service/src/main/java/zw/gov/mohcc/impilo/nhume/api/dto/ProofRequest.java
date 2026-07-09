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
        @JsonProperty("mark_delivered") Boolean markDelivered
) {}
