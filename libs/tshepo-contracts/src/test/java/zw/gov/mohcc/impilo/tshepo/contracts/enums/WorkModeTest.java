package zw.gov.mohcc.impilo.tshepo.contracts.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The invariants the mode vocabulary rests on. These are cheap assertions about a
 * closed enum, and they exist because each one is a rule that would otherwise live
 * only in a comment.
 */
class WorkModeTest {

    @Test
    void identifiedAccessIsTheOnlyLevelThatFoldsAClinicalRole() {
        for (WorkMode mode : WorkMode.values()) {
            assertThat(mode.grantsIdentifiedClinicalRead())
                    .as("%s", mode)
                    .isEqualTo(mode.clinicalDataAccess() == WorkMode.ClinicalDataAccess.IDENTIFIED);
        }
    }

    @Test
    void communityOutreachCarriesIdentifiedAccess() {
        // Outreach records screenings and immunizations against a named person. A mode
        // that could not do that would describe none of the actual work.
        assertThat(WorkMode.COMMUNITY_OUTREACH.clinicalDataAccess())
                .isEqualTo(WorkMode.ClinicalDataAccess.IDENTIFIED);
        assertThat(WorkMode.COMMUNITY_OUTREACH.grantsIdentifiedClinicalRead()).isTrue();
        assertThat(WorkMode.COMMUNITY_OUTREACH.defaultPurposeOfUse()).isEqualTo(PurposeOfUse.TREATMENT);
    }

    @Test
    void specimenTransportCarriesNoClinicalAccessAtAll() {
        // The whole point of the mode: a courier proves custody of a specimen and can
        // never read what it said.
        assertThat(WorkMode.SPECIMEN_TRANSPORT.clinicalDataAccess())
                .isEqualTo(WorkMode.ClinicalDataAccess.NONE);
        assertThat(WorkMode.SPECIMEN_TRANSPORT.grantsIdentifiedClinicalRead()).isFalse();
        assertThat(WorkMode.SPECIMEN_TRANSPORT.defaultPurposeOfUse()).isEqualTo(PurposeOfUse.OPERATIONS);
    }

    @Test
    void outreachAnchorsToAFacilityOrAProgramme() {
        // Outreach is dispatched either by a clinic's own team or by the programme
        // running a campaign; organisation is a different anchor and would exclude both.
        assertThat(WorkMode.COMMUNITY_OUTREACH.anchorKind())
                .isEqualTo(WorkMode.AnchorKind.FACILITY_OR_PROGRAMME);
    }

    @Test
    void parseNeverFallsBackToAMode() {
        // An unparseable mode must not resolve to anything, least of all a permissive one.
        assertThat(WorkMode.parse(null)).isEmpty();
        assertThat(WorkMode.parse("")).isEmpty();
        assertThat(WorkMode.parse("   ")).isEmpty();
        assertThat(WorkMode.parse("NOT_A_MODE")).isEmpty();
        assertThat(WorkMode.parse("clinical_care")).contains(WorkMode.CLINICAL_CARE);
        assertThat(WorkMode.parse(" community_outreach ")).contains(WorkMode.COMMUNITY_OUTREACH);
    }

    @Test
    void everyModeDeclaresAllThreeSemantics() {
        for (WorkMode mode : WorkMode.values()) {
            assertThat(mode.anchorKind()).as("anchorKind of %s", mode).isNotNull();
            assertThat(mode.clinicalDataAccess()).as("clinicalDataAccess of %s", mode).isNotNull();
            assertThat(mode.defaultPurposeOfUse()).as("defaultPurposeOfUse of %s", mode).isNotNull();
        }
    }
}
