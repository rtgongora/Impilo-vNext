package zw.gov.mohcc.impilo.wallet.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Card-settlement recording is fail-open: a blank intent, non-positive amount, or an
 * unreachable MusheX returns false (the payer is already debited — the payment stands
 * and is reconciled later) and never throws.
 */
class MushexSettlementClientTest {

    @Test
    @DisplayName("blank intent / non-positive amount → false, no call")
    void guards() {
        var client = new MushexSettlementClient("http://mushex:8102");
        assertFalse(client.recordCardPayment("", BigDecimal.TEN, UUID.randomUUID(), "ref"));
        assertFalse(client.recordCardPayment(null, BigDecimal.TEN, UUID.randomUUID(), "ref"));
        assertFalse(client.recordCardPayment("intent-1", BigDecimal.ZERO, UUID.randomUUID(), "ref"));
    }

    @Test
    @DisplayName("unreachable MusheX → false (payment stands), never throws")
    void unreachable() {
        var client = new MushexSettlementClient("http://127.0.0.1:1/");
        assertFalse(client.recordCardPayment("intent-1", new BigDecimal("10.00"), UUID.randomUUID(), "ref"));
    }
}
