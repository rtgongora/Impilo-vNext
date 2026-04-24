package zw.gov.mohcc.impilo.shared.visibility;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportVisibilityGuardTest {

    @Test
    void deniesWhenExportProhibited() {
        VisibilityProfile p = new VisibilityProfile(
                "DEIDENTIFIED_ROW_LEVEL", "MASKED", "NONE", false, "DEIDENTIFIED_PERSON_LEVEL",
                null, null, null, null, "PROHIBITED", true);
        assertTrue(ExportVisibilityGuard.deniesReportRun(p));
    }

    @Test
    void deniesWhenAggregateOnlyTier() {
        VisibilityProfile p = new VisibilityProfile(
                "AGGREGATE_ONLY", "NONE", "NONE", true, "AGGREGATE_SENSITIVE",
                null, null, null, null, "AGGREGATE_ONLY", false);
        assertTrue(ExportVisibilityGuard.deniesReportRun(p));
    }

    @Test
    void allowsWhenFullAuditedAndNotAggregate() {
        VisibilityProfile p = new VisibilityProfile(
                "IDENTIFIED_OPERATIONAL_ONLY", "FULL", "NONE", false, "IDENTIFIED_OPERATIONAL",
                null, null, null, null, "FULL_AUDITED", true);
        assertFalse(ExportVisibilityGuard.deniesReportRun(p));
    }

    @Test
    void nullProfileAllowsBackwardCompat() {
        assertFalse(ExportVisibilityGuard.deniesReportRun(null));
    }

    @Test
    void allowsRedactedExportPolicyForReportRun() {
        VisibilityProfile p = new VisibilityProfile(
                "DEIDENTIFIED_ROW_LEVEL", "MASKED", "NONE", false, "DEIDENTIFIED_PERSON_LEVEL",
                null, null, null, null, "REDACTED", true);
        assertFalse(ExportVisibilityGuard.deniesReportRun(p));
        assertTrue(ExportVisibilityGuard.requiresRedactedReportOutput(p));
    }

    @Test
    void requiresRedactionWhenSuppressFieldsPresent() {
        VisibilityProfile p = new VisibilityProfile(
                "IDENTIFIED_OPERATIONAL_ONLY", "FULL", "NONE", false, "IDENTIFIED_OPERATIONAL",
                null, null, List.of("patientName"), null, "FULL_AUDITED", true);
        assertTrue(ExportVisibilityGuard.requiresRedactedReportOutput(p));
    }
}
