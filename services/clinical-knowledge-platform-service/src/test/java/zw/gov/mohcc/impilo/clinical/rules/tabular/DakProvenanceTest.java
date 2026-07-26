package zw.gov.mohcc.impilo.clinical.rules.tabular;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The citation a recommendation carries: who says so, and is this the international guidance or a
 * local variation of it.
 */
class DakProvenanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static DakProvenance parse(String dakRef, String adaptation) {
        try {
            return DakProvenance.from(
                    dakRef == null ? null : MAPPER.readTree(dakRef),
                    adaptation == null ? null : MAPPER.readTree(adaptation));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aRuleWithNoProvenanceReturnsNullRatherThanAnEmptyCitation() {
        // Absence stays absence. An empty citation object on a response looks like an answer, and a
        // reader would have no way to tell it apart from a rule that genuinely cites nothing. The
        // traceability guard is what reports the gap; this class does not paper over it.
        assertThat(parse(null, null)).isNull();
    }

    @Test
    void aWhoRowIsCitedByArtefactAndRow() {
        var provenance = parse("""
                {"dak": "WHO_SMART_ANC_DT", "dakVersion": "2021-02-17",
                 "id": "ANC.DT.01", "row": "ANC.DT.01.R7"}
                """, """
                {"type": "ADOPTED_VERBATIM", "approvingAuthority": "PENDING_MOHCC_RATIFICATION"}
                """);

        assertThat(provenance.artefactId()).isEqualTo("ANC.DT.01");
        assertThat(provenance.row()).isEqualTo("ANC.DT.01.R7");
        assertThat(provenance.adapted()).isFalse();
        assertThat(provenance.nationallyAdded()).isFalse();
        assertThat(provenance.toMap()).containsEntry("adaptation_type", "ADOPTED_VERBATIM");
    }

    @Test
    void aStrengthenedRuleIsVisiblyNotTheWhoPosition() {
        // The case this distinction exists for. WHO's row for a convulsing pregnant woman outputs
        // "clinician's discretion"; the national rule is magnesium sulphate and immediate referral.
        // A reader must be able to tell that the instruction in front of them is ours.
        var provenance = parse("""
                {"dak": "WHO_SMART_ANC_DT", "id": "ANC.DT.01", "row": "ANC.DT.01.R3"}
                """, """
                {"type": "STRENGTHENED", "approvingAuthority": "PENDING_MOHCC_RATIFICATION"}
                """);

        assertThat(provenance.adapted()).isTrue();
    }

    @Test
    void aNationallyAddedRuleSaysSoRatherThanLookingLikeAMissingCitation() {
        // Cord prolapse has no WHO DAK row. "No reference" and "deliberately ours" are different
        // states, and only one of them is a defect.
        var provenance = parse(null, """
                {"type": "ADDED_NATIONAL", "approvingAuthority": "PENDING_MOHCC_RATIFICATION",
                 "evidenceRefs": ["EDLIZ 2025 obstetric emergencies"]}
                """);

        assertThat(provenance.nationallyAdded()).isTrue();
        assertThat(provenance.adapted()).isFalse();
        assertThat(provenance.artefactId()).isNull();
        assertThat(provenance.toMap()).doesNotContainKey("artefact_id");
    }
}
