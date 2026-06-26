package zw.gov.mohcc.impilo.pct.core.sorting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VisitTypeCatalogTest {

    @Test
    void outpatient_context_has_visit_types() {
        assertFalse(VisitTypeCatalog.forContext("OUTPATIENT").isEmpty());
        assertTrue(VisitTypeCatalog.forContext("outpatient").stream()
                .anyMatch(v -> v.code().equals("CHRONIC_CARE")));
    }

    @Test
    void unknown_visit_type_falls_back_to_emergency_assessment() {
        VisitTypeCatalog.VisitType vt = VisitTypeCatalog.resolve("OUTPATIENT", "NOT_A_REAL_TYPE");
        assertEquals("EMERGENCY_ASSESSMENT", vt.code());
        assertTrue(vt.fastTrackDefault());
    }

    @Test
    void unknown_context_falls_back_to_emergency_assessment() {
        VisitTypeCatalog.VisitType vt = VisitTypeCatalog.resolve("MARS_BASE", "OPD_NEW");
        assertEquals("EMERGENCY_ASSESSMENT", vt.code());
    }

    @Test
    void known_visit_type_resolves_with_fast_track_default() {
        VisitTypeCatalog.VisitType refill = VisitTypeCatalog.resolve("OUTPATIENT", "PHARMACY_REFILL");
        assertEquals("PHARMACY_REFILL", refill.code());
        assertTrue(refill.fastTrackDefault());

        VisitTypeCatalog.VisitType opdNew = VisitTypeCatalog.resolve("OUTPATIENT", "OPD_NEW");
        assertEquals("OPD_NEW", opdNew.code());
        assertFalse(opdNew.fastTrackDefault());
    }

    @Test
    void casualty_visit_types_are_all_fast_track() {
        assertTrue(VisitTypeCatalog.forContext("CASUALTY").stream()
                .allMatch(VisitTypeCatalog.VisitType::fastTrackDefault));
    }

    @Test
    void all_returns_every_context() {
        var all = VisitTypeCatalog.all();
        assertTrue(all.containsKey("OUTPATIENT"));
        assertTrue(all.containsKey("CASUALTY"));
        assertTrue(all.containsKey("COMMUNITY"));
        assertTrue(all.containsKey("VIRTUAL"));
    }
}
