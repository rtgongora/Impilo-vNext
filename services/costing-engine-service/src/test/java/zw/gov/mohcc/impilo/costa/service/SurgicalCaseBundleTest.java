package zw.gov.mohcc.impilo.costa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.costa.domain.enums.BillLineKind;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Wave 4 §5 — surgical-case bundle composition: theatre time + anaesthesia + implant + consumables + blood. */
@ExtendWith(MockitoExtension.class)
class SurgicalCaseBundleTest {

    @Mock BillService billService;
    private InpatientCostingService service;

    @BeforeEach
    void setUp() { service = new InpatientCostingService(billService); }

    @Test
    void composesAllBundleLinesWithCorrectCodesAndKinds() {
        service.postSurgicalCaseBundle("BILL-1", "LAP-CHOLE", 90, true, 2, 5, 1, "EP-1");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BillLineKind> kind = ArgumentCaptor.forClass(BillLineKind.class);
        ArgumentCaptor<BigDecimal> qty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(billService, times(5)).postLine(eq("BILL-1"), code.capture(), anyString(),
                kind.capture(), qty.capture(), eq(CostMethodType.TARIFF), eq("theatre.case.completed"),
                eq("EP-1"), anyMap());

        List<String> codes = code.getAllValues();
        assertEquals(List.of("THEATRE-TIME", "ANAESTHESIA-GA", "THEATRE-IMPLANT",
                "THEATRE-CONSUMABLE", "THEATRE-BLOOD"), codes);
        // Theatre-time qty = minutes; implant/consumable/blood qty = usage counts.
        assertEquals(0, BigDecimal.valueOf(90).compareTo(qty.getAllValues().get(0)));
        assertEquals(0, BigDecimal.valueOf(2).compareTo(qty.getAllValues().get(2)));
        assertEquals(0, BigDecimal.valueOf(5).compareTo(qty.getAllValues().get(3)));
        assertEquals(BillLineKind.IMPLANT, kind.getAllValues().get(2));
        assertEquals(BillLineKind.PRODUCT, kind.getAllValues().get(3));
        assertEquals(BillLineKind.OTHER, kind.getAllValues().get(4));
    }

    @Test
    void skipsAnaesthesiaAndZeroUsageLines() {
        service.postSurgicalCaseBundle("BILL-2", "APPENDIX", 45, false, 0, 0, 0, "EP-2");

        // Only theatre-time — no anaesthesia, no implants/consumables/blood.
        verify(billService, times(1)).postLine(eq("BILL-2"), eq("THEATRE-TIME"), anyString(),
                eq(BillLineKind.THEATRE), any(), eq(CostMethodType.TARIFF), anyString(), anyString(), anyMap());
        verifyNoMoreInteractions(billService);
    }
}
