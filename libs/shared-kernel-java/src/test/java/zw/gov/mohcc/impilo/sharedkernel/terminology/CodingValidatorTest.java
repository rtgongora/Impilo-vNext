package zw.gov.mohcc.impilo.sharedkernel.terminology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodingValidatorTest {

    // ── Coding validation ──────────────────────────────────────────────

    @Test
    void validLoinc_passesValidation() {
        Coding coding = Coding.of(CodingSystem.LOINC, "85354-9", "Blood pressure panel");
        List<String> issues = CodingValidator.validate(coding);
        assertTrue(issues.isEmpty(), "Expected no issues but got: " + issues);
    }

    @Test
    void validSnomedCt_passesValidation() {
        Coding coding = Coding.of(CodingSystem.SNOMED_CT, "73211009", "Diabetes mellitus");
        List<String> issues = CodingValidator.validate(coding);
        assertTrue(issues.isEmpty(), "Expected no issues but got: " + issues);
    }

    @Test
    void validIcd11_passesValidation() {
        Coding coding = Coding.of(CodingSystem.ICD_11, "BA00", "Cholera");
        List<String> issues = CodingValidator.validate(coding);
        assertTrue(issues.isEmpty(), "Expected no issues but got: " + issues);
    }

    @Test
    void validAtc_passesValidation() {
        Coding coding = Coding.of(CodingSystem.ATC, "J01CA04", "Amoxicillin");
        List<String> issues = CodingValidator.validate(coding);
        assertTrue(issues.isEmpty(), "Expected no issues but got: " + issues);
    }

    @Test
    void validCvx_passesValidation() {
        Coding coding = Coding.of(CodingSystem.CVX, "207", "COVID-19 vaccine");
        List<String> issues = CodingValidator.validate(coding);
        assertTrue(issues.isEmpty(), "Expected no issues but got: " + issues);
    }

    @Test
    void nullCoding_failsValidation() {
        List<String> issues = CodingValidator.validate((Coding) null);
        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().contains("null"));
    }

    @Test
    void missingSystem_failsValidation() {
        Coding coding = Coding.of((String) null, "12345-6", "Test");
        List<String> issues = CodingValidator.validate(coding);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.contains("system")));
    }

    @Test
    void missingCode_failsValidation() {
        Coding coding = Coding.of(CodingSystem.LOINC, "", "Test");
        List<String> issues = CodingValidator.validate(coding);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.contains("code")));
    }

    @Test
    void unknownSystem_flaggedAsUnrecognized() {
        Coding coding = Coding.of("http://example.com/fake", "ABC", "Test");
        List<String> issues = CodingValidator.validate(coding);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.contains("Unrecognized")));
    }

    @Test
    void invalidLoincFormat_flagged() {
        // LOINC must be digits-digit (e.g., "85354-9"), not "INVALID"
        Coding coding = Coding.of(CodingSystem.LOINC, "INVALID", "Bad code");
        List<String> issues = CodingValidator.validate(coding);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.contains("format")));
    }

    @Test
    void invalidSnomedFormat_flagged() {
        // SNOMED CT must be 6-18 digits
        Coding coding = Coding.of(CodingSystem.SNOMED_CT, "ABC", "Not a SNOMED code");
        List<String> issues = CodingValidator.validate(coding);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.contains("format")));
    }

    // ── CodeableConcept validation ─────────────────────────────────────

    @Test
    void validCodeableConcept_passes() {
        CodeableConcept concept = CodeableConcept.ofSingle(
                CodingSystem.ICD_11, "BA00", "Cholera");
        List<String> issues = CodingValidator.validate(concept);
        assertTrue(issues.isEmpty(), "Expected no issues but got: " + issues);
    }

    @Test
    void multiCodedConcept_passes() {
        CodeableConcept concept = CodeableConcept.builder()
                .coding(CodingSystem.ICD_11, "BA00", "Cholera")
                .coding(CodingSystem.SNOMED_CT, "63650001", "Cholera")
                .text("Cholera")
                .build();
        List<String> issues = CodingValidator.validate(concept);
        assertTrue(issues.isEmpty(), "Expected no issues but got: " + issues);
    }

    @Test
    void textOnlyConcept_passes() {
        CodeableConcept concept = CodeableConcept.ofText("Free text diagnosis");
        List<String> issues = CodingValidator.validate(concept);
        assertTrue(issues.isEmpty(), "Expected no issues but got: " + issues);
    }

    @Test
    void emptyConcept_fails() {
        CodeableConcept concept = CodeableConcept.builder().build();
        List<String> issues = CodingValidator.validate(concept);
        assertFalse(issues.isEmpty());
    }

    @Test
    void nullConcept_fails() {
        List<String> issues = CodingValidator.validate((CodeableConcept) null);
        assertEquals(1, issues.size());
    }

    @Test
    void conceptWithRequiredSystem_passesWhenPresent() {
        CodeableConcept concept = CodeableConcept.ofSingle(
                CodingSystem.LOINC, "85354-9", "Blood pressure panel");
        List<String> issues = CodingValidator.validate(concept, CodingSystem.LOINC);
        assertTrue(issues.isEmpty(), "Expected no issues but got: " + issues);
    }

    @Test
    void conceptWithRequiredSystem_failsWhenAbsent() {
        CodeableConcept concept = CodeableConcept.ofSingle(
                CodingSystem.SNOMED_CT, "73211009", "Diabetes mellitus");
        List<String> issues = CodingValidator.validate(concept, CodingSystem.ICD_11);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.contains("ICD-11")));
    }

    // ── isRecognizedSystem ─────────────────────────────────────────────

    @Test
    void isRecognizedSystem_trueForKnownUris() {
        assertTrue(CodingValidator.isRecognizedSystem("http://loinc.org"));
        assertTrue(CodingValidator.isRecognizedSystem("http://snomed.info/sct"));
        assertTrue(CodingValidator.isRecognizedSystem("http://id.who.int/icd/release/11"));
    }

    @Test
    void isRecognizedSystem_falseForUnknownOrNull() {
        assertFalse(CodingValidator.isRecognizedSystem("http://example.com"));
        assertFalse(CodingValidator.isRecognizedSystem(null));
    }
}
