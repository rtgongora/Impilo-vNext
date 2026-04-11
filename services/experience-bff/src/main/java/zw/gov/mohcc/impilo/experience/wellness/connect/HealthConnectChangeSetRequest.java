package zw.gov.mohcc.impilo.experience.wellness.connect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Impilo "Health Connect equivalence" ingest envelope.
 * <p>Field names mirror Android Health Connect JSON conventions where practical
 * ({@code metadata}, {@code startTime}, {@code endTime}, {@code count}, {@code volume}).
 * Supported record types: {@code Steps}, {@code Hydration}, {@code SleepSession}, {@code HeartRate}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HealthConnectChangeSetRequest(
        @NotBlank String patientId,
        @Valid @NotNull DataOrigin dataOrigin,
        @Size(max = 64) List<String> grantedScopes,
        @Valid @Size(max = 200) List<HealthRecord> records
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataOrigin(
            /** e.g. {@code android_health_connect}, {@code apple_healthkit}, {@code web_simulator} */
            @NotBlank String platform,
            String appPackage,
            String appVersion
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HealthRecord(
            /** Stable id from the source system (HC client record id or UUID) — used for idempotent ingest. */
            @NotBlank String id,
            @NotBlank String type,
            @Valid Metadata metadata,
            @NotBlank String startTime,
            String endTime,
            /** Steps — total count in interval */
            Long count,
            /** Hydration — liters in interval (mapped to ml on activity day of startTime) */
            BigDecimal volumeLiters,
            /** Sleep — hours in session (optional if endTime set, else derived) */
            BigDecimal hoursSlept,
            /** Sleep — 1–5 self rating */
            Integer sleepQuality,
            /** Heart rate — single BPM for interval start */
            Long beatsPerMinute,
            @Valid @Size(max = 500) List<HeartRateSample> samples
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Metadata(
                @JsonProperty("recording_method") String recordingMethod,
                String device
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record HeartRateSample(
                @NotBlank String time,
                long beatsPerMinute
        ) {}
    }
}
