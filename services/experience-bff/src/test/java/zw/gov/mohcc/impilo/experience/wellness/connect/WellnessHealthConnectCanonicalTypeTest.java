package zw.gov.mohcc.impilo.experience.wellness.connect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WellnessHealthConnectCanonicalTypeTest {

    @Test
    void stripsRecordSuffixAndNormalizesAliases() {
        assertEquals("STEPS", WellnessHealthConnectIngestService.canonicalRecordType("StepsRecord"));
        assertEquals("DISTANCE", WellnessHealthConnectIngestService.canonicalRecordType("DistanceRecord"));
        assertEquals("SLEEPSESSION", WellnessHealthConnectIngestService.canonicalRecordType("SleepSessionRecord"));
        assertEquals("ACTIVE_ENERGY", WellnessHealthConnectIngestService.canonicalRecordType("ActiveCaloriesBurnedRecord"));
        assertEquals("TOTAL_ENERGY", WellnessHealthConnectIngestService.canonicalRecordType("TotalCaloriesBurnedRecord"));
        assertEquals("FLOORS_CLIMBED", WellnessHealthConnectIngestService.canonicalRecordType("FloorsClimbedRecord"));
        assertEquals("EXERCISE_SESSION", WellnessHealthConnectIngestService.canonicalRecordType("ExerciseSessionRecord"));
        assertEquals("HRV_RMSSD", WellnessHealthConnectIngestService.canonicalRecordType("HeartRateVariabilityRmssdRecord"));
        assertEquals("WHEELCHAIR_PUSHES", WellnessHealthConnectIngestService.canonicalRecordType("WheelchairPushesRecord"));
        assertEquals("ELEVATION_GAINED", WellnessHealthConnectIngestService.canonicalRecordType("ElevationGainedDuringExerciseRecord"));
        assertEquals("ACTIVE_MINUTES", WellnessHealthConnectIngestService.canonicalRecordType("ActiveMinutesBurnedRecord"));
        assertEquals("RESPIRATORY_RATE", WellnessHealthConnectIngestService.canonicalRecordType("RespiratoryRateRecord"));
        assertEquals("MENSTRUATION_FLOW", WellnessHealthConnectIngestService.canonicalRecordType("MenstruationFlowRecord"));
    }
}
