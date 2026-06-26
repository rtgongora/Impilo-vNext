package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import zw.gov.mohcc.impilo.costa.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;
import zw.gov.mohcc.impilo.costa.domain.repository.ChargeRecordRepository;
import zw.gov.mohcc.impilo.costa.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.costa.engine.CostEngine;
import zw.gov.mohcc.impilo.costa.engine.CostEngineRegistry;
import zw.gov.mohcc.impilo.costa.engine.CostResult;
import zw.gov.mohcc.impilo.costa.rules.ChargingRuleEngine;
import zw.gov.mohcc.impilo.costa.rules.RuleContext;
import zw.gov.mohcc.impilo.costa.rules.RuleResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChargeRecordServiceTest {

    @Mock ChargeRecordRepository chargeRecordRepository;
    @Mock EventOutboxRepository outboxRepository;
    @Mock CostEngineRegistry costEngineRegistry;
    @Mock CostEngine tariffEngine;
    @Mock ChargingRuleEngine chargingRuleEngine;

    ChargeRecordService service;

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FACILITY = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        service = new ChargeRecordService(chargeRecordRepository, outboxRepository,
                costEngineRegistry, chargingRuleEngine, new ObjectMapper());
        when(costEngineRegistry.resolveOrDefault(eq(CostMethodType.TARIFF), eq(CostMethodType.TARIFF)))
                .thenReturn(tariffEngine);
        when(chargeRecordRepository.existsByTenantIdAndSourceTypeAndSourceRef(any(), any(), any()))
                .thenReturn(false);
        // By default, no charging rules configured -> pass the tariff cost straight through.
        when(chargingRuleEngine.evaluate(any())).thenAnswer(inv -> {
            RuleContext c = inv.getArgument(0);
            return RuleResult.passThrough(c.costAmount());
        });
    }

    private JsonNode teleconsultEvent() throws Exception {
        return new ObjectMapper().readTree("{"
                + "\"eventId\":\"ref-1:TELECONSULT_COMPLETED\","
                + "\"tenantId\":\"" + TENANT + "\","
                + "\"referralId\":\"ref-1\","
                + "\"patientCpid\":\"CPID-1\","
                + "\"encounterId\":\"ENC-1\","
                + "\"facilityId\":\"" + FACILITY + "\","
                + "\"specialty\":\"CARDIOLOGY\","
                + "\"modality\":\"virtual\""
                + "}");
    }

    @Test
    void ingestTeleconsultCompleted_pricesViaSpecialtyTariff() throws Exception {
        // specialty-specific tariff resolves to $50.00
        when(tariffEngine.compute(eq(TENANT), eq(FACILITY), eq("TELECONSULT_CARDIOLOGY"),
                any(BigDecimal.class), any()))
                .thenReturn(CostResult.of(new BigDecimal("50.00"), BigDecimal.ONE,
                        CostMethodType.TARIFF, "TARIFF:TC_CARD", Map.of("tariff_code", "TC_CARD")));

        ChargeRecordEntity charge = service.ingestTeleconsultCompleted(teleconsultEvent());

        assertNotNull(charge);
        ArgumentCaptor<ChargeRecordEntity> captor = ArgumentCaptor.forClass(ChargeRecordEntity.class);
        verify(chargeRecordRepository).save(captor.capture());
        ChargeRecordEntity saved = captor.getValue();
        assertEquals(0, new BigDecimal("50.00").compareTo(saved.getChargeAmount()));
        assertEquals("TELECONSULT", saved.getChargeType());
        assertEquals("ref-1", saved.getSourceRef());
        assertTrue(saved.getMetadataJson().contains("\"tariffSource\":\"TARIFF:TC_CARD\""));
        assertTrue(saved.getMetadataJson().contains("\"pendingPricing\":false"));

        // generic fallback never consulted because specialty tariff matched
        verify(tariffEngine, org.mockito.Mockito.never())
                .compute(any(), any(), eq("TELECONSULT"), any(), any());

        // CHARGE_CREATED emitted
        ArgumentCaptor<EventOutboxEntity> evCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(evCaptor.capture());
        assertEquals("CHARGE_CREATED", evCaptor.getValue().getEventType());
    }

    @Test
    void ingestTeleconsultCompleted_fallsBackToGenericTariff() throws Exception {
        when(tariffEngine.compute(eq(TENANT), eq(FACILITY), eq("TELECONSULT_CARDIOLOGY"),
                any(BigDecimal.class), any()))
                .thenReturn(CostResult.zero(CostMethodType.TARIFF, "no specialty tariff"));
        when(tariffEngine.compute(eq(TENANT), eq(FACILITY), eq("TELECONSULT"),
                any(BigDecimal.class), any()))
                .thenReturn(CostResult.of(new BigDecimal("25.00"), BigDecimal.ONE,
                        CostMethodType.TARIFF, "TARIFF:TELECONSULT", Map.of()));

        ChargeRecordEntity charge = service.ingestTeleconsultCompleted(teleconsultEvent());

        ArgumentCaptor<ChargeRecordEntity> captor = ArgumentCaptor.forClass(ChargeRecordEntity.class);
        verify(chargeRecordRepository).save(captor.capture());
        assertEquals(0, new BigDecimal("25.00").compareTo(captor.getValue().getChargeAmount()));
        assertTrue(captor.getValue().getMetadataJson().contains("\"pendingPricing\":false"));
    }

    @Test
    void ingestTeleconsultCompleted_recordsOpenWhenNoTariffConfigured() throws Exception {
        when(tariffEngine.compute(any(), any(), any(), any(BigDecimal.class), any()))
                .thenReturn(CostResult.zero(CostMethodType.TARIFF, "no tariff configured"));

        ChargeRecordEntity charge = service.ingestTeleconsultCompleted(teleconsultEvent());

        ArgumentCaptor<ChargeRecordEntity> captor = ArgumentCaptor.forClass(ChargeRecordEntity.class);
        verify(chargeRecordRepository).save(captor.capture());
        ChargeRecordEntity saved = captor.getValue();
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getChargeAmount()));
        assertEquals("OPEN", saved.getChargeStatus());
        assertTrue(saved.getMetadataJson().contains("\"pendingPricing\":true"));
    }

    @Test
    void ingestTeleconsultCompleted_appliesChargingRuleDiscount() throws Exception {
        when(tariffEngine.compute(eq(TENANT), eq(FACILITY), eq("TELECONSULT_CARDIOLOGY"),
                any(BigDecimal.class), any()))
                .thenReturn(CostResult.of(new BigDecimal("50.00"), BigDecimal.ONE,
                        CostMethodType.TARIFF, "TARIFF:TC_CARD", Map.of()));
        // a 20% discount rule reduces the $50.00 tariff to $40.00
        doReturn(new RuleResult(
                new BigDecimal("40.00"), BigDecimal.ZERO, new BigDecimal("20"),
                false, false, List.of("Pensioner discount"), Map.of()))
                .when(chargingRuleEngine).evaluate(any());

        service.ingestTeleconsultCompleted(teleconsultEvent());

        ArgumentCaptor<ChargeRecordEntity> captor = ArgumentCaptor.forClass(ChargeRecordEntity.class);
        verify(chargeRecordRepository).save(captor.capture());
        ChargeRecordEntity saved = captor.getValue();
        assertEquals(0, new BigDecimal("40.00").compareTo(saved.getChargeAmount()));
        assertEquals("OPEN", saved.getChargeStatus());
        assertTrue(saved.isBillableFlag());
        assertTrue(saved.getMetadataJson().contains("\"tariffTotal\":50.00"));
    }

    @Test
    void ingestTeleconsultCompleted_excludedByChargingRuleIsNonBillable() throws Exception {
        when(tariffEngine.compute(eq(TENANT), eq(FACILITY), eq("TELECONSULT_CARDIOLOGY"),
                any(BigDecimal.class), any()))
                .thenReturn(CostResult.of(new BigDecimal("50.00"), BigDecimal.ONE,
                        CostMethodType.TARIFF, "TARIFF:TC_CARD", Map.of()));
        // an exemption rule excludes the charge entirely
        doReturn(new RuleResult(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, true, List.of("Indigent exemption"), Map.of()))
                .when(chargingRuleEngine).evaluate(any());

        ChargeRecordEntity charge = service.ingestTeleconsultCompleted(teleconsultEvent());

        ArgumentCaptor<ChargeRecordEntity> captor = ArgumentCaptor.forClass(ChargeRecordEntity.class);
        verify(chargeRecordRepository).save(captor.capture());
        ChargeRecordEntity saved = captor.getValue();
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getChargeAmount()));
        assertEquals("EXCLUDED", saved.getChargeStatus());
        assertTrue(!saved.isBillableFlag());
        assertTrue(saved.getMetadataJson().contains("\"excluded\":true"));
        // still emits CHARGE_CREATED for audit
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }
}
