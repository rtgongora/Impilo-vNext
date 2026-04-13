package zw.gov.mohcc.impilo.sharedkernel.terminology;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CodeableConceptTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ofSingle_createsSingleCodingConcept() {
        CodeableConcept concept = CodeableConcept.ofSingle(
                CodingSystem.LOINC, "85354-9", "Blood pressure panel");

        assertEquals(1, concept.getCoding().size());
        assertEquals("http://loinc.org", concept.getCoding().getFirst().getSystem());
        assertEquals("85354-9", concept.getCoding().getFirst().getCode());
        assertEquals("Blood pressure panel", concept.getCoding().getFirst().getDisplay());
        assertEquals("Blood pressure panel", concept.getText());
    }

    @Test
    void builder_createsMultiCodingConcept() {
        CodeableConcept concept = CodeableConcept.builder()
                .coding(CodingSystem.ICD_11, "BA00", "Cholera")
                .coding(CodingSystem.SNOMED_CT, "63650001", "Cholera")
                .text("Cholera due to Vibrio cholerae")
                .build();

        assertEquals(2, concept.getCoding().size());
        assertTrue(concept.hasCoding(CodingSystem.ICD_11));
        assertTrue(concept.hasCoding(CodingSystem.SNOMED_CT));
        assertFalse(concept.hasCoding(CodingSystem.LOINC));
        assertEquals("Cholera due to Vibrio cholerae", concept.getText());
    }

    @Test
    void getCoding_bySystem_returnsCorrectCoding() {
        CodeableConcept concept = CodeableConcept.builder()
                .coding(CodingSystem.ICD_11, "BA00", "Cholera")
                .coding(CodingSystem.SNOMED_CT, "63650001", "Cholera")
                .build();

        Optional<Coding> icd = concept.getCoding(CodingSystem.ICD_11);
        assertTrue(icd.isPresent());
        assertEquals("BA00", icd.get().getCode());

        Optional<Coding> snomed = concept.getCoding(CodingSystem.SNOMED_CT);
        assertTrue(snomed.isPresent());
        assertEquals("63650001", snomed.get().getCode());
    }

    @Test
    void getCoding_byUri_returnsCorrectCoding() {
        CodeableConcept concept = CodeableConcept.ofSingle(
                CodingSystem.LOINC, "85354-9", "Blood pressure panel");

        Optional<Coding> loinc = concept.getCoding("http://loinc.org");
        assertTrue(loinc.isPresent());
        assertEquals("85354-9", loinc.get().getCode());
    }

    @Test
    void ofText_createsTextOnlyConcept() {
        CodeableConcept concept = CodeableConcept.ofText("Free text diagnosis");

        assertTrue(concept.getCoding().isEmpty());
        assertEquals("Free text diagnosis", concept.getText());
        assertFalse(concept.isEmpty());
    }

    @Test
    void getDisplayText_prefersFirstCodingDisplay() {
        CodeableConcept concept = CodeableConcept.builder()
                .coding(CodingSystem.LOINC, "85354-9", "Blood pressure panel")
                .text("BP Panel")
                .build();

        assertEquals("Blood pressure panel", concept.getDisplayText());
    }

    @Test
    void getDisplayText_fallsBackToText() {
        CodeableConcept concept = CodeableConcept.ofText("Free text description");
        assertEquals("Free text description", concept.getDisplayText());
    }

    @Test
    void isEmpty_trueForEmptyConcept() {
        CodeableConcept concept = CodeableConcept.builder().build();
        assertTrue(concept.isEmpty());
    }

    @Test
    void isEmpty_falseWithCoding() {
        CodeableConcept concept = CodeableConcept.ofSingle(
                CodingSystem.LOINC, "85354-9", "BP");
        assertFalse(concept.isEmpty());
    }

    @Test
    void codingList_isImmutable() {
        CodeableConcept concept = CodeableConcept.ofSingle(
                CodingSystem.LOINC, "85354-9", "BP");
        assertThrows(UnsupportedOperationException.class,
                () -> concept.getCoding().add(Coding.of(CodingSystem.SNOMED_CT, "12345", "Test")));
    }

    @Test
    void serialization_roundTrip() throws Exception {
        CodeableConcept original = CodeableConcept.builder()
                .coding(CodingSystem.ICD_11, "BA00", "Cholera")
                .coding(CodingSystem.SNOMED_CT, "63650001", "Cholera")
                .text("Cholera")
                .build();

        String json = mapper.writeValueAsString(original);

        assertTrue(json.contains("\"system\""));
        assertTrue(json.contains("\"code\""));
        assertTrue(json.contains("http://id.who.int/icd/release/11"));
        assertTrue(json.contains("BA00"));

        CodeableConcept deserialized = mapper.readValue(json, CodeableConcept.class);
        assertEquals(2, deserialized.getCoding().size());
        assertEquals("Cholera", deserialized.getText());
    }

    @Test
    void coding_toString_includesSystemAndCode() {
        Coding coding = Coding.of(CodingSystem.LOINC, "85354-9", "Blood pressure panel");
        String str = coding.toString();
        assertTrue(str.contains("http://loinc.org"));
        assertTrue(str.contains("85354-9"));
    }

    @Test
    void coding_getCodingSystem_returnsEnumForKnownUri() {
        Coding coding = Coding.of(CodingSystem.SNOMED_CT, "73211009", "Diabetes mellitus");
        assertEquals(CodingSystem.SNOMED_CT, coding.getCodingSystem());
    }

    @Test
    void coding_getCodingSystem_returnsNullForUnknownUri() {
        Coding coding = Coding.of("http://example.com/custom", "ABC", "Custom");
        assertNull(coding.getCodingSystem());
    }

    @Test
    void coding_equality_basedOnSystemAndCode() {
        Coding a = Coding.of(CodingSystem.LOINC, "85354-9", "Blood pressure panel");
        Coding b = Coding.of(CodingSystem.LOINC, "85354-9", "BP Panel");
        Coding c = Coding.of(CodingSystem.LOINC, "8480-6", "Systolic BP");

        assertEquals(a, b); // Same system+code, different display
        assertNotEquals(a, c); // Same system, different code
    }
}
