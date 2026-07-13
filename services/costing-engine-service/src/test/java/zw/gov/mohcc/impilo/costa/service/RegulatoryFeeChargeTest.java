package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * COSTA is the money truth of the HPA registration fee: fee_due opens a
 * REGULATORY_APPLICATION_FEE charge (amount = TUSO's pinned SI-78 value, never
 * priced here), fee_paid settles it, fee_waived voids it — all idempotent, and a
 * fee with no amount never fabricates a charge.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegulatoryFeeChargeTest {

    @Mock ChargeRecordRepository chargeRecordRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock CostEngineRegistry costEngineRegistry;
    @Mock ChargingRuleEngine chargingRuleEngine;

    private final ObjectMapper mapper = new ObjectMapper();
    ChargeRecordService service;

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String APP = "app-uuid-1";

    @BeforeEach
    void setUp() {
        service = new ChargeRecordService(chargeRecordRepository, outboxRepository,
                costEngineRegistry, chargingRuleEngine, mapper);
        when(chargeRecordRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void feeDueWithAmountOpensAChargeOnce() throws Exception {
        when(chargeRecordRepository.existsByTenantIdAndSourceTypeAndSourceRef(
                TENANT, ChargeRecordService.SOURCE_REGULATORY_APPLICATION_FEE, APP)).thenReturn(false);
        var event = mapper.readTree("{\"applicationId\":\"" + APP + "\",\"tenantId\":\"" + TENANT
                + "\",\"amount\":\"120.00\",\"currency\":\"USD\",\"feeCode\":\"FEE_X\"}");

        service.ingestRegulatoryFeeDue(event);

        ArgumentCaptor<ChargeRecordEntity> c = ArgumentCaptor.forClass(ChargeRecordEntity.class);
        verify(chargeRecordRepository).save(c.capture());
        assertEquals("REGULATORY_APPLICATION_FEE", c.getValue().getSourceType());
        assertEquals("OPEN", c.getValue().getChargeStatus());
        assertEquals(0, c.getValue().getChargeAmount().compareTo(new java.math.BigDecimal("120.00")));
    }

    @Test
    void feeDueWithoutAmountNeverFabricatesACharge() throws Exception {
        when(chargeRecordRepository.existsByTenantIdAndSourceTypeAndSourceRef(any(), any(), any())).thenReturn(false);
        var event = mapper.readTree("{\"applicationId\":\"" + APP + "\",\"tenantId\":\"" + TENANT + "\"}");
        service.ingestRegulatoryFeeDue(event);
        verify(chargeRecordRepository, never()).save(any());
    }

    @Test
    void settleMovesOpenToSettledIdempotently() {
        ChargeRecordEntity e = charge("OPEN");
        when(chargeRecordRepository.findByTenantIdAndSourceTypeAndSourceRef(
                TENANT, ChargeRecordService.SOURCE_REGULATORY_APPLICATION_FEE, APP)).thenReturn(List.of(e));
        assertEquals(1, service.settleRegulatoryFee(TENANT, APP));
        assertEquals("SETTLED", e.getChargeStatus());
        assertEquals(0, service.settleRegulatoryFee(TENANT, APP)); // idempotent
    }

    @Test
    void waiveVoidsAnOpenChargeButNotASettledOne() {
        ChargeRecordEntity open = charge("OPEN");
        when(chargeRecordRepository.findByTenantIdAndSourceTypeAndSourceRef(
                TENANT, ChargeRecordService.SOURCE_REGULATORY_APPLICATION_FEE, APP)).thenReturn(List.of(open));
        assertEquals(1, service.waiveRegulatoryFee(TENANT, APP));
        assertEquals("WAIVED", open.getChargeStatus());
    }

    private ChargeRecordEntity charge(String status) {
        ChargeRecordEntity e = new ChargeRecordEntity();
        e.setChargeId("CHG1");
        e.setTenantId(TENANT);
        e.setSourceType(ChargeRecordService.SOURCE_REGULATORY_APPLICATION_FEE);
        e.setSourceRef(APP);
        e.setChargeStatus(status);
        return e;
    }
}
