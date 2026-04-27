package zw.gov.mohcc.impilo.costa.api.dto;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.costa.api.dto.finance.TariffApplicationResult;
import zw.gov.mohcc.impilo.costa.domain.enums.BillingTimingMode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TariffApplicationResultTest {

    @Test
    void toMap_includesBillingTimingModeAndLiabilities() {
        TariffApplicationResult r = new TariffApplicationResult(
                42L,
                "TARIFF-DEMO",
                BillingTimingMode.PRE_SERVICE,
                "100.00",
                "20.00",
                "70.00",
                "5.00",
                "5.00"
        );
        Map<String, Object> m = r.toMap();
        assertEquals(42L, m.get("tariff_list_id"));
        assertEquals("TARIFF-DEMO", m.get("tariff_external_code"));
        assertEquals("PRE_SERVICE", m.get("billing_timing_mode"));
        assertEquals("100.00", m.get("tariffed_price"));
        assertEquals("20.00", m.get("patient_liability"));
        assertEquals("70.00", m.get("payer_liability"));
    }

    @Test
    void toMap_omitsNullBillingTimingMode() {
        TariffApplicationResult r = new TariffApplicationResult(
                1L, "X", null, "1", "1", "0", "0", "0");
        Map<String, Object> m = r.toMap();
        assertFalse(m.containsKey("billing_timing_mode"));
    }
}
