package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargeRecordEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargeRecordRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.engine.CostEngineRegistry;
import zw.gov.mohcc.impilo.costa.rules.ChargingRuleEngine;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the settle/refund leg of the Msika Flow marketplace charge: a paid order
 * moves its MARKETPLACE_ORDER charge OPEN -> SETTLED (idempotent), a refunded order
 * moves it -> REFUNDED, and neither double-applies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceChargeSettleTest {

    @Mock ChargeRecordRepository chargeRecordRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock CostEngineRegistry costEngineRegistry;
    @Mock ChargingRuleEngine chargingRuleEngine;

    ChargeRecordService service;

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ORDER = "ORDER12345678901234567";

    @BeforeEach
    void setUp() {
        service = new ChargeRecordService(chargeRecordRepository, outboxRepository,
                costEngineRegistry, chargingRuleEngine, new ObjectMapper());
    }

    private ChargeRecordEntity openCharge() {
        ChargeRecordEntity e = new ChargeRecordEntity();
        e.setChargeId("CHG1");
        e.setTenantId(TENANT);
        e.setSourceType(ChargeRecordService.SOURCE_MSIKA_FLOW_ORDER_PRICED);
        e.setSourceRef(ORDER);
        e.setChargeStatus("OPEN");
        return e;
    }

    @Test
    void settle_openCharge_movesToSettled() {
        ChargeRecordEntity e = openCharge();
        when(chargeRecordRepository.findByTenantIdAndSourceTypeAndSourceRef(
                TENANT, ChargeRecordService.SOURCE_MSIKA_FLOW_ORDER_PRICED, ORDER)).thenReturn(List.of(e));
        when(chargeRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int n = service.settleMsikaFlowOrder(TENANT, ORDER);

        assertEquals(1, n);
        assertEquals("SETTLED", e.getChargeStatus());
    }

    @Test
    void settle_alreadySettled_isIdempotent() {
        ChargeRecordEntity e = openCharge();
        e.setChargeStatus("SETTLED");
        when(chargeRecordRepository.findByTenantIdAndSourceTypeAndSourceRef(
                TENANT, ChargeRecordService.SOURCE_MSIKA_FLOW_ORDER_PRICED, ORDER)).thenReturn(List.of(e));

        int n = service.settleMsikaFlowOrder(TENANT, ORDER);

        assertEquals(0, n);
        verify(chargeRecordRepository, never()).save(any());
    }

    @Test
    void settle_noCharge_returnsZero() {
        when(chargeRecordRepository.findByTenantIdAndSourceTypeAndSourceRef(
                TENANT, ChargeRecordService.SOURCE_MSIKA_FLOW_ORDER_PRICED, ORDER)).thenReturn(List.of());

        assertEquals(0, service.settleMsikaFlowOrder(TENANT, ORDER));
    }

    @Test
    void refund_settledCharge_movesToRefunded() {
        ChargeRecordEntity e = openCharge();
        e.setChargeStatus("SETTLED");
        when(chargeRecordRepository.findByTenantIdAndSourceTypeAndSourceRef(
                TENANT, ChargeRecordService.SOURCE_MSIKA_FLOW_ORDER_PRICED, ORDER)).thenReturn(List.of(e));
        when(chargeRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int n = service.refundMsikaFlowOrder(TENANT, ORDER);

        assertEquals(1, n);
        assertEquals("REFUNDED", e.getChargeStatus());
    }

    @Test
    void refund_alreadyRefunded_isIdempotent() {
        ChargeRecordEntity e = openCharge();
        e.setChargeStatus("REFUNDED");
        when(chargeRecordRepository.findByTenantIdAndSourceTypeAndSourceRef(
                TENANT, ChargeRecordService.SOURCE_MSIKA_FLOW_ORDER_PRICED, ORDER)).thenReturn(List.of(e));

        assertEquals(0, service.refundMsikaFlowOrder(TENANT, ORDER));
        verify(chargeRecordRepository, never()).save(any());
    }
}
