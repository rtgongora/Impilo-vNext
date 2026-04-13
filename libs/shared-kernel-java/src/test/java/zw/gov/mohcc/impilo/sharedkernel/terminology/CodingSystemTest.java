package zw.gov.mohcc.impilo.sharedkernel.terminology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class CodingSystemTest {

    @ParameterizedTest
    @EnumSource(CodingSystem.class)
    void everySystem_hasNonBlankUri(CodingSystem system) {
        assertNotNull(system.getUri());
        assertFalse(system.getUri().isBlank());
    }

    @ParameterizedTest
    @EnumSource(CodingSystem.class)
    void everySystem_hasNonBlankDisplayName(CodingSystem system) {
        assertNotNull(system.getDisplayName());
        assertFalse(system.getDisplayName().isBlank());
    }

    @ParameterizedTest
    @EnumSource(CodingSystem.class)
    void fromUri_roundTrips(CodingSystem system) {
        assertEquals(system, CodingSystem.fromUri(system.getUri()));
    }

    @Test
    void fromUri_throwsForUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> CodingSystem.fromUri("http://example.com/unknown"));
    }

    @Test
    void isKnown_trueForLoinc() {
        assertTrue(CodingSystem.isKnown("http://loinc.org"));
    }

    @Test
    void isKnown_trueForSnomedCt() {
        assertTrue(CodingSystem.isKnown("http://snomed.info/sct"));
    }

    @Test
    void isKnown_trueForIcd11() {
        assertTrue(CodingSystem.isKnown("http://id.who.int/icd/release/11"));
    }

    @Test
    void isKnown_falseForUnknown() {
        assertFalse(CodingSystem.isKnown("http://example.com/fake"));
    }

    @Test
    void isKnown_falseForNull() {
        assertFalse(CodingSystem.isKnown(null));
    }

    @Test
    void primarySystems_haveCorrectUris() {
        assertEquals("http://loinc.org", CodingSystem.LOINC.getUri());
        assertEquals("http://snomed.info/sct", CodingSystem.SNOMED_CT.getUri());
        assertEquals("http://id.who.int/icd/release/11", CodingSystem.ICD_11.getUri());
        assertEquals("http://www.whocc.no/atc", CodingSystem.ATC.getUri());
        assertEquals("http://dicom.nema.org/resources/ontology/DCM", CodingSystem.DICOM.getUri());
    }

    @ParameterizedTest
    @EnumSource(CodingSystem.class)
    void uris_areUnique(CodingSystem system) {
        long count = java.util.Arrays.stream(CodingSystem.values())
                .filter(s -> s.getUri().equals(system.getUri()))
                .count();
        assertEquals(1, count, "Duplicate URI found: " + system.getUri());
    }
}
