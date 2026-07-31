package zw.gov.mohcc.impilo.medicine.pharm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenalDosingAdvisorTest {

    @Test
    void missingEgfrIsUnknownNeverInvented() {
        var advice = RenalDosingAdvisor.advise(null, "BIGUANIDE");

        assertThat(advice.computed()).isFalse();
        assertThat(advice.level()).isEqualTo(RenalDosingAdvisor.Level.UNKNOWN);
        assertThat(advice.egfr()).isNull();
        assertThat(advice.message()).contains("eGFR is not available");
    }

    @Test
    void unrecognisedDrugClassIsUnknown() {
        var advice = RenalDosingAdvisor.advise(55.0, "NOT_A_REAL_CLASS");

        assertThat(advice.computed()).isFalse();
        assertThat(advice.level()).isEqualTo(RenalDosingAdvisor.Level.UNKNOWN);
        assertThat(advice.message()).contains("not recognised");
    }

    @Test
    void metforminBelowThirtyIsAdjust() {
        var advice = RenalDosingAdvisor.advise(25.0, "BIGUANIDE");

        assertThat(advice.computed()).isTrue();
        assertThat(advice.level()).isEqualTo(RenalDosingAdvisor.Level.ADJUST);
        assertThat(advice.drugClass()).isEqualTo("BIGUANIDE");
        assertThat(advice.message()).contains("contraindicated");
    }

    @Test
    void metforminInThirtyToFortyFourBandIsCaution() {
        var advice = RenalDosingAdvisor.advise(38.0, "biguanide");

        assertThat(advice.level()).isEqualTo(RenalDosingAdvisor.Level.CAUTION);
        assertThat(advice.message()).contains("30–44");
    }

    @Test
    void nsaidAtNormalEgfrIsNoChange() {
        var advice = RenalDosingAdvisor.advise(90.0, "NSAID");

        assertThat(advice.level()).isEqualTo(RenalDosingAdvisor.Level.NO_CHANGE);
    }

    @Test
    void renallyClearedAceInhibitorBelowSixtyIsAdjust() {
        var advice = RenalDosingAdvisor.advise(52.0, "ACE_INHIBITOR");

        assertThat(advice.level()).isEqualTo(RenalDosingAdvisor.Level.ADJUST);
        assertThat(advice.message()).contains("renally cleared");
    }

    @Test
    void thiazideBelowThirtyIsAdjust() {
        var advice = RenalDosingAdvisor.advise(22.0, "THIAZIDE");

        assertThat(advice.level()).isEqualTo(RenalDosingAdvisor.Level.ADJUST);
        assertThat(advice.message()).contains("ineffective");
    }
}
