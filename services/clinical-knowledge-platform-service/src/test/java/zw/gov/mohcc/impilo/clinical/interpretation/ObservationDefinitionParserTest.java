package zw.gov.mohcc.impilo.clinical.interpretation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.clinical.interpretation.model.QualifiedInterval;
import zw.gov.mohcc.impilo.clinical.interpretation.model.Sex;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObservationDefinitionParser}: code extraction and merging of reference + critical
 * qualifiedInterval entries by applicability (gender/age), with fail-closed skipping.
 */
class ObservationDefinitionParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) {
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void parse_mergesReferenceAndCritical_intoOneInterval() {
        JsonNode r = json("""
            {
              "resourceType":"ObservationDefinition",
              "code":{"coding":[
                {"system":"http://loinc.org","code":"2823-3","display":"Potassium"},
                {"system":"http://zibo.local/lims","code":"K"}]},
              "qualifiedInterval":[
                {"category":"reference","gender":"male","age":{"low":{"value":18,"unit":"a"},"high":{"value":120,"unit":"a"}},
                 "range":{"low":{"value":3.5,"unit":"mmol/L"},"high":{"value":5.1,"unit":"mmol/L"}}},
                {"category":"critical","gender":"male","age":{"low":{"value":18,"unit":"a"},"high":{"value":120,"unit":"a"}},
                 "range":{"low":{"value":2.5,"unit":"mmol/L"},"high":{"value":6.5,"unit":"mmol/L"}}}
              ]
            }
            """);

        List<QualifiedInterval> intervals = ObservationDefinitionParser.parse(r, "art-1", "1.0.0", "urn:od:k");

        assertThat(intervals).hasSize(1);
        QualifiedInterval qi = intervals.get(0);
        assertThat(qi.sex()).isEqualTo(Sex.MALE);
        assertThat(qi.ageLowYears()).isEqualTo(18.0);
        assertThat(qi.ageHighYears()).isEqualTo(120.0);
        assertThat(qi.low()).isEqualByComparingTo("3.5");
        assertThat(qi.high()).isEqualByComparingTo("5.1");
        assertThat(qi.criticalLow()).isEqualByComparingTo("2.5");
        assertThat(qi.criticalHigh()).isEqualByComparingTo("6.5");
        assertThat(qi.unit()).isEqualTo("mmol/L");
        assertThat(qi.version()).isEqualTo("1.0.0");
        assertThat(qi.artifactId()).isEqualTo("art-1");
    }

    @Test
    void parse_separateGenderGroups_yieldTwoIntervals() {
        JsonNode r = json("""
            {
              "code":{"coding":[{"system":"http://loinc.org","code":"718-7"}]},
              "qualifiedInterval":[
                {"category":"reference","gender":"male","range":{"low":{"value":13,"unit":"g/dL"},"high":{"value":17,"unit":"g/dL"}}},
                {"category":"reference","gender":"female","range":{"low":{"value":12,"unit":"g/dL"},"high":{"value":15,"unit":"g/dL"}}}
              ]
            }
            """);

        List<QualifiedInterval> intervals = ObservationDefinitionParser.parse(r, "a", "1", "u");

        assertThat(intervals).hasSize(2);
        assertThat(intervals).anyMatch(qi -> qi.sex() == Sex.MALE && qi.low().compareTo(new java.math.BigDecimal("13")) == 0);
        assertThat(intervals).anyMatch(qi -> qi.sex() == Sex.FEMALE && qi.high().compareTo(new java.math.BigDecimal("15")) == 0);
    }

    @Test
    void parse_ageInMonths_convertedToYears() {
        JsonNode r = json("""
            {
              "code":{"coding":[{"system":"http://loinc.org","code":"X"}]},
              "qualifiedInterval":[
                {"category":"reference","age":{"low":{"value":0,"unit":"mo"},"high":{"value":12,"unit":"mo"}},
                 "range":{"low":{"value":1,"unit":"g/L"},"high":{"value":2,"unit":"g/L"}}}
              ]
            }
            """);

        List<QualifiedInterval> intervals = ObservationDefinitionParser.parse(r, "a", "1", "u");
        assertThat(intervals).hasSize(1);
        assertThat(intervals.get(0).ageHighYears()).isEqualTo(1.0); // 12 months
    }

    @Test
    void parse_noQualifiedInterval_returnsEmpty() {
        assertThat(ObservationDefinitionParser.parse(json("{\"code\":{}}"), "a", "1", "u")).isEmpty();
    }

    @Test
    void codeExtraction_loincAndLocal() {
        JsonNode r = json("""
            {"code":{"coding":[
              {"system":"http://loinc.org","code":"2823-3"},
              {"system":"http://zibo.local/lims","code":"K"}]}}
            """);
        assertThat(ObservationDefinitionParser.loincOf(r)).isEqualTo("2823-3");
        assertThat(ObservationDefinitionParser.localCodeOf(r)).isEqualTo("K");
    }
}
