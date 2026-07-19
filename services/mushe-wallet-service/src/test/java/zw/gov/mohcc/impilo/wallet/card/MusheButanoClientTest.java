package zw.gov.mohcc.impilo.wallet.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The card PHR fetch is fail-open-for-sync: a blank CPID or an unreachable BUTANO
 * returns null, so the caller keeps the PENDING_SYNC marker rather than writing
 * garbage or blocking the encounter. (The happy path returns the real IPS bundle
 * live against a deployed BUTANO.)
 */
class MusheButanoClientTest {

    @Test
    @DisplayName("blank cpid → null (no call)")
    void blankCpidReturnsNull() {
        var client = new MusheButanoClient("http://butano:8090");
        assertNull(client.fetchIpsSummary("", UUID.randomUUID()));
        assertNull(client.fetchIpsSummary(null, UUID.randomUUID()));
    }

    @Test
    @DisplayName("unreachable BUTANO → null (keep PENDING_SYNC), never throws")
    void unreachableReturnsNull() {
        var client = new MusheButanoClient("http://127.0.0.1:1/");
        assertNull(client.fetchIpsSummary("cpid-123", UUID.randomUUID()));
    }
}
