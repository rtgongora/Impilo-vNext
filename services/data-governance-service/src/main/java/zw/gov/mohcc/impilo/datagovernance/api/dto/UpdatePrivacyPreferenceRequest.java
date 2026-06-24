package zw.gov.mohcc.impilo.datagovernance.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Update a user's privacy display preference. {@code userId} identifies the subject;
 * remaining fields are optional and clamped to safe values server-side.
 */
public record UpdatePrivacyPreferenceRequest(
        @JsonProperty("userId") @NotBlank String userId,
        @JsonProperty("defaultLevel") String defaultLevel,
        @JsonProperty("autoLockMinutes") Integer autoLockMinutes,
        @JsonProperty("screenShareMode") Boolean screenShareMode
) {}
