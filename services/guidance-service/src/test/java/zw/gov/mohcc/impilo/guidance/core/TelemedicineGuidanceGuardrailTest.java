package zw.gov.mohcc.impilo.guidance.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TM-B10 guardrail (G6): Nompilo NEVER diagnoses, prescribes, or alters clinical facts —
 * enforced as a deterministic lint over the telemedicine guidance catalogue (V016 seed).
 * Danger-sign guidance is escalate-only: routes to the emergency surface, audited, and
 * carries zero clinical-instruction wording.
 */
class TelemedicineGuidanceGuardrailTest {

    private static final List<String> FORBIDDEN_CLINICAL_INSTRUCTIONS = List.of(
            "you should take",   // prescribing
            "your diagnosis",    // diagnosing
            "you have a condition",
            " dose ",            // dosing instructions
            "mg of",             // dosing instructions
            "stop your medication",
            "start taking");

    private String catalogue() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(
                "/db/migration/V016__nompilo_telemedicine_guidance.sql")) {
            assertThat(in).as("V016 telemedicine guidance seed must exist").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }
    }

    @Test
    void catalogueContainsNoClinicalInstructions() throws IOException {
        String sql = catalogue();
        for (String forbidden : FORBIDDEN_CLINICAL_INSTRUCTIONS) {
            assertThat(sql)
                    .as("Nompilo guidance must never contain clinical-instruction wording: '%s'", forbidden)
                    .doesNotContain(forbidden);
        }
    }

    @Test
    void dangerSignItemIsEscalateOnlyAuditedAndCritical() throws IOException {
        String sql = catalogue();
        assertThat(sql).contains("telemed_danger_signs");
        // The escalate-only contract: emergency CTA, CRITICAL severity, audited, and an explicit
        // statement that Nompilo makes no symptom judgement.
        assertThat(sql).contains("'/emergency'");
        assertThat(sql).contains("nompilo cannot judge symptoms");
        int idx = sql.indexOf("telemed_danger_signs");
        String row = sql.substring(idx, sql.indexOf(");", idx) + 1 > idx ? Math.min(idx + 700, sql.length()) : sql.length());
        assertThat(row).as("danger-sign item must be CRITICAL").contains("'critical'");
        assertThat(row).as("danger-sign item must be audited (audit_required TRUE)").contains("true");
        assertThat(row).as("danger-sign item must be ESCALATION type").contains("'escalation'");
    }

    @Test
    void providerRiskPromptStatesItNeverReplacesClinicalJudgement() throws IOException {
        String sql = catalogue();
        assertThat(sql).contains("telemed_provider_redflag");
        assertThat(sql).contains("never replaces your clinical judgement");
    }
}
