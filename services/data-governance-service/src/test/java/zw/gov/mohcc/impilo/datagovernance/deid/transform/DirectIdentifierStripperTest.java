package zw.gov.mohcc.impilo.datagovernance.deid.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DirectIdentifierStripper — allowlist, fail-closed")
class DirectIdentifierStripperTest {

    private final DirectIdentifierStripper stripper = new DirectIdentifierStripper();

    @Test
    @DisplayName("columns not on the allowlist are dropped")
    void nonAllowlistedDropped() {
        Map<String, Object> out = stripper.strip(
                Map.of("gender", "F", "occupation", "nurse"),
                Set.of("gender"));
        assertThat(out).containsOnlyKeys("gender");
    }

    @Test
    @DisplayName("allowlisted non-identifier columns survive")
    void allowlistedSurvive() {
        Map<String, Object> out = stripper.strip(
                Map.of("gender", "F", "district", "Harare", "diagnosis", "A00"),
                Set.of("gender", "district", "diagnosis"));
        assertThat(out).containsOnlyKeys("gender", "district", "diagnosis");
    }

    @Test
    @DisplayName("identifier-named columns are dropped EVEN WHEN allowlisted (fail-closed)")
    void bannedEvenIfAllowlisted() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Tariro Moyo");
        row.put("healthId", "ZW-HID-1");
        row.put("cpid", "CPID-001");
        row.put("phone_number", "+263771234567");
        row.put("nationalId", "63-123456A78");
        row.put("gender", "F");
        Map<String, Object> out = stripper.strip(row,
                Set.of("name", "healthId", "cpid", "phone_number", "nationalId", "gender"));
        assertThat(out).containsOnlyKeys("gender");
    }

    @Test
    @DisplayName("healthId is dropped in every naming variant")
    void healthIdVariantsDropped() {
        for (String variant : new String[]{"healthId", "health_id", "HEALTH-ID", "Health Id", "patientHealthId"}) {
            Map<String, Object> out = stripper.strip(
                    Map.of(variant, "ZW-HID-1", "gender", "F"),
                    Set.of(variant, "gender"));
            assertThat(out).as("variant %s must be stripped", variant).containsOnlyKeys("gender");
        }
    }

    @Test
    @DisplayName("name/phone/address/email/nid variants are all identifier columns")
    void identifierVariantsRecognised() {
        for (String column : new String[]{
                "name", "firstName", "surname", "patient_name",
                "phone", "phoneNumber", "msisdn", "mobile",
                "address", "home_address", "email", "emailAddress",
                "nid", "nationalId", "national_id", "passportNumber", "cpid", "CPID"}) {
            assertThat(stripper.isDirectIdentifier(column))
                    .as("%s must be treated as a direct identifier", column)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("non-identifier analytic columns are not banned")
    void analyticColumnsNotBanned() {
        for (String column : new String[]{"gender", "district", "diagnosis", "age_band",
                "visit_date", "subject_token", "ward_type"}) {
            assertThat(stripper.isDirectIdentifier(column))
                    .as("%s must not be treated as a direct identifier", column)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("empty allowlist drops every column (fail-closed default)")
    void emptyAllowlistDropsEverything() {
        Map<String, Object> out = stripper.strip(
                Map.of("gender", "F", "district", "Harare"), Set.of());
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("null row and null allowlist are handled fail-closed")
    void nullInputsFailClosed() {
        assertThat(stripper.strip(null, Set.of("gender"))).isEmpty();
        assertThat(stripper.strip(Map.of("gender", "F"), null)).isEmpty();
    }

    @Test
    @DisplayName("allowlist matching is naming-variant insensitive")
    void allowlistVariantInsensitive() {
        Map<String, Object> out = stripper.strip(
                Map.of("visit_date", "2026-07-01"),
                Set.of("visitDate"));
        assertThat(out).containsOnlyKeys("visit_date");
    }

    @Test
    @DisplayName("input row is not mutated")
    void inputNotMutated() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Tariro");
        row.put("gender", "F");
        stripper.strip(row, Set.of("gender"));
        assertThat(row).containsKeys("name", "gender");
    }
}
