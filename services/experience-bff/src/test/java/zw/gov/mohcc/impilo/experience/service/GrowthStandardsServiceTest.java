package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GrowthStandardsServiceTest {

    private final GrowthStandardsService service = new GrowthStandardsService(new ObjectMapper());

    @Test
    void computesWhoUnder5WeightForAgeAtMedianAsZero() {
        GrowthStandardsService.GrowthAssessment assessment = service.assess(
                new GrowthStandardsService.PatientContext(LocalDate.parse("2026-01-01"), "MALE"),
                new GrowthStandardsService.GrowthMeasurement(
                        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                        BigDecimal.valueOf(3.3464d),
                        BigDecimal.valueOf(49.8842d),
                        null,
                        BigDecimal.valueOf(34.4618d),
                        null,
                        "LENGTH"
                )
        );

        assertEquals(0, assessment.ageDays());
        assertEquals("WHO_2006_CHILD_GROWTH_STANDARDS", assessment.standard());
        assertNotNull(assessment.weightForAge());
        assertEquals(BigDecimal.valueOf(0.00d).setScale(2), assessment.weightForAge().zScore());
        assertEquals(BigDecimal.valueOf(50.0d).setScale(1), assessment.weightForAge().percentile());
        assertEquals(BigDecimal.valueOf(0.00d).setScale(2), assessment.lengthHeightForAge().zScore());
        assertEquals(BigDecimal.valueOf(0.00d).setScale(2), assessment.headCircumferenceForAge().zScore());
    }

    @Test
    void returnsNoWhoScoresOutsideUnderFiveRange() {
        GrowthStandardsService.GrowthAssessment assessment = service.assess(
                new GrowthStandardsService.PatientContext(LocalDate.parse("2018-01-01"), "FEMALE"),
                new GrowthStandardsService.GrowthMeasurement(
                        OffsetDateTime.parse("2026-01-02T00:00:00Z"),
                        BigDecimal.valueOf(20.0d),
                        null,
                        BigDecimal.valueOf(110.0d),
                        null,
                        null,
                        "HEIGHT"
                )
        );

        assertNull(assessment.standard());
        assertNull(assessment.weightForAge());
        assertNull(assessment.lengthHeightForAge());
        assertNull(assessment.bodyMassIndexForAge());
        assertNull(assessment.headCircumferenceForAge());
    }
}
