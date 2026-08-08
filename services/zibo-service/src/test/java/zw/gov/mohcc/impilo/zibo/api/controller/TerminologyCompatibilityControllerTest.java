package zw.gov.mohcc.impilo.zibo.api.controller;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.zibo.core.MappingService;
import zw.gov.mohcc.impilo.zibo.core.ValidationEngine;
import zw.gov.mohcc.impilo.zibo.domain.ValidationSeverity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The compatibility routes must return the shape each client already parses.
 *
 * <p>Two services have been calling routes ZIBO never served: oros
 * ({@code GET /v1/mappings/translate} against {@code POST /v1/map}) and butano
 * ({@code POST /api/v1/terminology/validate}, mapped by nothing at all). Both degrade silently, so
 * neither failure surfaced.</p>
 *
 * <p><b>The path is the easy half.</b> ZIBO's house style wraps every payload in
 * {@code ApiResponse<T>} under {@code data}; both of these clients read a flat body — oros does
 * {@code body.get("targetCode")}, butano deserialises {@code {valid, display, message}}. An alias
 * that returned the envelope would answer 200 and hand both callers a null: a route that looks
 * fixed and is not, which is strictly worse than the 404 it replaced. These tests pin the shape,
 * because that is the part that fails quietly.</p>
 */
@ExtendWith(MockitoExtension.class)
class TerminologyCompatibilityControllerTest {

    @Mock private MappingService mappingService;
    @Mock private ValidationEngine validationEngine;

    @InjectMocks private TerminologyCompatibilityController controller;

    // ── oros: GET /v1/mappings/translate ──────────────────────────────────────────────────────

    @Test
    void translateReturnsTargetCodeAtTheTopLevel_notUnderData() {
        when(mappingService.mapCode("LIMS", "FBC", "http://loinc.org"))
                .thenReturn(new MappingService.MappingResult("http://loinc.org", "58410-2",
                        "CBC panel", "equivalent", new BigDecimal("1.00"), UUID.randomUUID()));

        Map<String, Object> body = controller.translate("LIMS", "FBC", "http://loinc.org").getBody();

        assertThat(body)
                .describedAs("oros reads body.get(\"targetCode\") directly — an ApiResponse "
                        + "envelope would 200 and hand it a null")
                .containsEntry("targetCode", "58410-2");
        assertThat(body).doesNotContainKey("data");
        assertThat(body).containsEntry("targetDisplay", "CBC panel");
        assertThat(body).containsEntry("equivalence", "equivalent");
    }

    @Test
    void aMissingMappingIsTwoHundredWithANullTargetCode_notAnError() {
        // "No mapping exists" is an answer, not a failure, and oros already maps a null to
        // Optional.empty(). Matches POST /v1/map, which does the same.
        when(mappingService.mapCode(any(), any(), any()))
                .thenReturn(new MappingService.MappingResult("http://snomed.info/sct",
                        null, null, null, null, null));

        var response = controller.translate("LIMS", "UNMAPPED", "http://snomed.info/sct");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("targetCode", null);
    }

    // ── butano: POST /api/v1/terminology/validate ─────────────────────────────────────────────

    @Test
    void validateReturnsTheFlatRecordButanoDeserialises() {
        when(validationEngine.validateCoding(any(), any(), any(), any(), any()))
                .thenReturn(new ValidationEngine.ValidationResult(true, java.util.List.of()));

        Map<String, Object> body = controller.validateTerminology(
                new TerminologyCompatibilityController.ValidateCodingBody(
                        "http://loinc.org", "8480-6")).getBody();

        assertThat(body)
                .describedAs("butano deserialises {valid, display, message} — every key must be "
                        + "present at the top level")
                .containsKeys("valid", "display", "message");
        assertThat(body).containsEntry("valid", true);
        assertThat(body).doesNotContainKey("data");
    }

    @Test
    void anInvalidCodeCarriesTheReasonBackToButano() {
        when(validationEngine.validateCoding(any(), any(), any(), any(), any()))
                .thenReturn(new ValidationEngine.ValidationResult(false, java.util.List.of(
                        new ValidationEngine.ValidationIssue(ValidationSeverity.ERROR,
                                "code-invalid", "Code 'XXX' not found in CodeSystem",
                                "http://loinc.org"))));

        Map<String, Object> body = controller.validateTerminology(
                new TerminologyCompatibilityController.ValidateCodingBody(
                        "http://loinc.org", "XXX")).getBody();

        assertThat(body).containsEntry("valid", false);
        assertThat((String) body.get("message")).contains("not found");
    }

    @Test
    void policyModeIsNotOverridden_soScopeAssignmentStillDecides() {
        // A facility held to STRICT must stay STRICT; passing an explicit mode here would make the
        // compatibility route quietly ignore the pack assignment.
        when(validationEngine.validateCoding(any(), any(), any(), any(), any()))
                .thenReturn(new ValidationEngine.ValidationResult(true, java.util.List.of()));

        controller.validateTerminology(new TerminologyCompatibilityController.ValidateCodingBody(
                "http://loinc.org", "8480-6"));

        org.mockito.Mockito.verify(validationEngine)
                .validateCoding("http://loinc.org", "8480-6", null, null, null);
    }
}
