package zw.gov.mohcc.impilo.experience.wellness.connect;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Impilo Health Connect parity ingest envelope.
 * <p>Aligns with Android Health Connect datatype names (often {@code *Record} suffix), interval
 * fields ({@code startTime}, {@code endTime}, zone offsets), and common metrics. Unknown JSON keys
 * are ignored. See {@code GET .../manifest} for the supported type matrix.</p>
 *
 * See Android Health Connect data types (developer.android.com health-connect).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthConnectChangeSetRequest(
        @NotBlank String patientId,
        @Valid @NotNull DataOrigin dataOrigin,
        @Size(max = 128) List<String> grantedScopes,
        @Valid @Size(max = 500) List<HealthRecord> records
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataOrigin(
            /** e.g. {@code android_health_connect}, {@code apple_healthkit} */
            @NotBlank String platform,
            String appPackage,
            String appVersion
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HealthRecord(
            @NotBlank String id,
            @NotBlank String type,
            @Valid Metadata metadata,
            @NotBlank String startTime,
            String endTime,
            /**
             * HC zone offsets (ISO-8601 offset strings). When {@code startTime} has no zone, combined with
             * local date-time for {@code activity_date} bucketing on daily rollups.
             */
            String startZoneOffset,
            String endZoneOffset,

            /** Steps count — HC {@code StepsRecord} count. */
            Long count,

            /** Wheelchair pushes — merged into {@code wellness_activities.steps} (HC parity). */
            @JsonAlias({"wheelchairPushes", "wheelchair_push_count"})
            Long wheelchairPushes,

            /** Liters — HC {@code HydrationRecord} volume often serialized as {@code volume} (liters). */
            @JsonProperty("volumeLiters")
            @JsonAlias({"volume", "volumeInLiters"})
            BigDecimal volumeLiters,

            BigDecimal hoursSlept,
            Integer sleepQuality,
            /** HC {@code SleepSessionRecord} stages */
            @Valid @Size(max = 200) List<SleepStageEntry> sleepStages,

            Long beatsPerMinute,
            @Valid @Size(max = 500) List<HeartRateSample> samples,

            /** Meters — HC {@code DistanceRecord#getDistance()} in meters when flattened for transport */
            @JsonAlias({"distance", "lengthInMeters", "distanceMeters"})
            BigDecimal distanceMeters,

            @JsonAlias({"floors", "floorsClimbed"})
            Long floorsClimbed,

            @JsonAlias({"activeEnergyKcal", "activeCalories", "energyActiveKcal"})
            BigDecimal activeEnergyKcal,

            @JsonAlias({"totalEnergyKcal", "totalCalories", "basalAndActiveEnergyKcal"})
            BigDecimal totalEnergyKcal,

            @JsonAlias({"weightKg", "weight"})
            BigDecimal weightKg,

            @JsonAlias({"heightCm", "height"})
            BigDecimal heightCm,

            @JsonAlias({"bodyFatPercent", "bodyFat"})
            BigDecimal bodyFatPercent,

            Long bloodPressureSystolic,
            Long bloodPressureDiastolic,

            @JsonAlias({"bloodGlucoseMgPerDl", "bloodGlucose"})
            BigDecimal bloodGlucoseMgDl,

            @JsonAlias({"oxygenSaturation", "spo2", "oxygenSaturationPercent"})
            BigDecimal oxygenSaturationPercent,

            @JsonAlias({"restingHeartRate", "restingHeartRateBpm"})
            Long restingHeartRateBpm,

            @JsonAlias({"hrvRmssdMs", "heartRateVariabilityRmssd"})
            Long hrvRmssdMilliseconds,

            @JsonAlias({"nutritionEnergyKcal", "energy"})
            BigDecimal nutritionEnergyKcal,

            String exerciseType,
            String exerciseTitle,
            BigDecimal exerciseEnergyKcal,
            BigDecimal exerciseDistanceMeters,

            @JsonAlias({"elevationGainedMeters", "elevationGained", "elevation", "verticalDistanceMeters"})
            BigDecimal elevationGainedMeters,

            @JsonAlias({"activeMinutes", "activeMinutesBurned", "activeMinutesInt"})
            Integer activeMinutes,

            @JsonAlias({"stepsCadenceRpm", "stepsCadence", "rpm"})
            BigDecimal stepsCadenceRpm,

            @JsonAlias({"cyclingPedalingCadenceRpm", "cyclingPedalingCadence", "pedalingCadenceRpm"})
            BigDecimal cyclingPedalingCadenceRpm,

            @JsonAlias({"powerWatts", "power"})
            BigDecimal powerWatts,

            @JsonAlias({"speedMetersPerSecond", "speed", "speedMs"})
            BigDecimal speedMetersPerSecond,

            @JsonAlias({"respiratoryRateBpm", "respiratoryRate", "breathsPerMinute"})
            BigDecimal respiratoryRateBpm,

            @JsonAlias({"bodyTemperatureCelsius", "bodyTemperature", "temperatureCelsius"})
            BigDecimal bodyTemperatureCelsius,

            @JsonAlias({"basalMetabolicRateKcal", "bmrKcal", "basalMetabolicRate"})
            BigDecimal basalMetabolicRateKcal,

            @JsonAlias({"leanBodyMassKg", "leanBodyMass"})
            BigDecimal leanBodyMassKg,

            @JsonAlias({"boneMassKg", "boneMass"})
            BigDecimal boneMassKg,

            @JsonAlias({"vo2MaxMlKgMin", "vo2Max"})
            BigDecimal vo2MaxMlKgMin,

            @JsonAlias({"bodyWaterMassKg", "bodyWaterMass"})
            BigDecimal bodyWaterMassKg,

            @JsonAlias({"mindfulnessSessionMinutes", "mindfulnessMinutes"})
            BigDecimal mindfulnessSessionMinutes,

            @JsonAlias({"nutritionProteinG", "proteinGrams", "protein"})
            BigDecimal nutritionProteinG,

            @JsonAlias({"nutritionCarbsG", "carbohydratesGrams", "carbsGrams", "carbs"})
            BigDecimal nutritionCarbsG,

            @JsonAlias({"nutritionFatG", "totalFatGrams", "fatGrams", "fat"})
            BigDecimal nutritionFatG
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Metadata(
                @JsonProperty("recording_method") String recordingMethod,
                String device,
                String clientRecordId,
                String dataOriginPackage
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record HeartRateSample(
                @NotBlank String time,
                long beatsPerMinute
        ) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record SleepStageEntry(
                @NotBlank String stage,
                @NotBlank String startTime,
                @NotBlank String endTime
        ) {}
    }
}
