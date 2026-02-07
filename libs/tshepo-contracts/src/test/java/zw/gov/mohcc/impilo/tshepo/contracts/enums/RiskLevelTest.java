package zw.gov.mohcc.impilo.tshepo.contracts.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskLevelTest {

    @Test
    void fromScore_0_returnsLow() {
        assertThat(RiskLevel.fromScore(0)).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void fromScore_30_returnsLow() {
        assertThat(RiskLevel.fromScore(30)).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void fromScore_31_returnsMedium() {
        assertThat(RiskLevel.fromScore(31)).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void fromScore_60_returnsMedium() {
        assertThat(RiskLevel.fromScore(60)).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void fromScore_61_returnsHigh() {
        assertThat(RiskLevel.fromScore(61)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void fromScore_80_returnsHigh() {
        assertThat(RiskLevel.fromScore(80)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void fromScore_81_returnsBlocked() {
        assertThat(RiskLevel.fromScore(81)).isEqualTo(RiskLevel.BLOCKED);
    }

    @Test
    void fromScore_100_returnsBlocked() {
        assertThat(RiskLevel.fromScore(100)).isEqualTo(RiskLevel.BLOCKED);
    }
}
