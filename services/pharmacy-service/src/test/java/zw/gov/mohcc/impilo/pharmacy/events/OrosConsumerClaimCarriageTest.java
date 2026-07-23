package zw.gov.mohcc.impilo.pharmacy.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pharmacy.core.DispenseEngine;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseOrderRepository;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OF-B12 claim carriage on the frozen {@code oros.order.placed} contract:
 * the serialized OrderEntity's {@code externalRefs} JSON document carries
 * {@code prescriptionClaimId}; malformed values degrade to an unbound episode
 * (the §13.3.1 sweep surfaces it) — the medication still flows.
 */
@ExtendWith(MockitoExtension.class)
class OrosConsumerClaimCarriageTest {

    @Mock DispenseEngine dispenseEngine;
    @Mock DispenseOrderRepository orderRepository;

    private OrosConsumer consumer() {
        return new OrosConsumer(dispenseEngine, orderRepository, new ObjectMapper());
    }

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID FACILITY = UUID.randomUUID();
    private static final UUID CLAIM = UUID.randomUUID();

    private String message(String externalRefsJsonEscaped) {
        return "{"
                + "\"orderId\":\"01OROSORDERDDDDDDDDDDDDDDD\","
                + "\"tenantId\":\"" + TENANT + "\","
                + "\"facilityId\":\"" + FACILITY + "\","
                + "\"patientCpid\":\"CPID-1\","
                + "\"orderType\":\"PHARMACY\""
                + (externalRefsJsonEscaped != null
                        ? ",\"externalRefs\":\"" + externalRefsJsonEscaped + "\"" : "")
                + "}";
    }

    @Test
    @DisplayName("externalRefs.prescriptionClaimId is extracted and bound onto the episode")
    void claimIdExtractedFromExternalRefs() {
        when(orderRepository.findByOrosOrderId(any())).thenReturn(Optional.empty());

        consumer().consumeOrosOrder(message(
                "{\\\"prescriptionClaimId\\\":\\\"" + CLAIM + "\\\"}"));

        verify(dispenseEngine).createFromOrosOrder(
                eq("01OROSORDERDDDDDDDDDDDDDDD"), eq("CPID-1"), eq(FACILITY),
                any(), anyList(), eq(CLAIM));
    }

    @Test
    @DisplayName("no externalRefs → episode created unbound (claim id null)")
    void missingRefsYieldsNullClaim() {
        when(orderRepository.findByOrosOrderId(any())).thenReturn(Optional.empty());

        consumer().consumeOrosOrder(message(null));

        verify(dispenseEngine).createFromOrosOrder(
                any(), any(), any(), any(), anyList(), isNull());
    }

    @Test
    @DisplayName("malformed claim id degrades to unbound — the medication still flows")
    void malformedClaimIdDegradesToNull() {
        when(orderRepository.findByOrosOrderId(any())).thenReturn(Optional.empty());

        consumer().consumeOrosOrder(message(
                "{\\\"prescriptionClaimId\\\":\\\"not-a-uuid\\\"}"));

        verify(dispenseEngine).createFromOrosOrder(
                any(), any(), any(), any(), anyList(), isNull());
    }

    @Test
    @DisplayName("non-PHARMACY orders remain skipped")
    void nonPharmacySkipped() {
        consumer().consumeOrosOrder(
                "{\"orderId\":\"X\",\"orderType\":\"LAB\"}");

        verifyNoInteractions(dispenseEngine);
    }
}
