package zw.gov.mohcc.impilo.mushex.integration.paynow;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Paynow wire-protocol invariants: the SHA-512 value-concatenation hash, its
 * verification (tamper ⇒ reject), form parsing, and status semantics. The
 * HTTP legs are exercised in the money-rails rig against a protocol simulator.
 */
class PaynowProtocolTest {

    private PaynowClient client(String key) {
        return new PaynowClient(RestClient.builder(), "http://localhost:9",
                "12345", key, "http://impilo/result", "http://impilo/return");
    }

    @Test
    void hash_isUppercaseSha512_ofConcatenatedValuesPlusKey() {
        PaynowClient c = client("secret-key");
        String h = c.hash(List.of("12345", "REF-1", "10.00"));

        assertEquals(128, h.length(), "SHA-512 hex is 128 chars");
        assertEquals(h.toUpperCase(), h, "hash must be uppercase hex");
        // Deterministic: same inputs, same hash; different key, different hash.
        assertEquals(h, c.hash(List.of("12345", "REF-1", "10.00")));
        assertNotEquals(h, client("other-key").hash(List.of("12345", "REF-1", "10.00")));
        // Value-order sensitivity — the protocol hashes values in field order.
        assertNotEquals(h, c.hash(List.of("REF-1", "12345", "10.00")));
    }

    @Test
    void verifyHash_acceptsGenuineMessage_rejectsTamperedAmount() {
        PaynowClient c = client("secret-key");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("reference", "01INTENT");
        fields.put("paynowreference", "PN-778");
        fields.put("amount", "43.14");
        fields.put("status", "Paid");
        fields.put("hash", c.hash(fields.values()));

        assertTrue(c.verifyHash(fields), "genuine message must verify");

        Map<String, String> tampered = new LinkedHashMap<>(fields);
        tampered.put("amount", "1.00"); // attacker shrinks the amount
        assertFalse(c.verifyHash(tampered), "tampered amount must be rejected");

        Map<String, String> noHash = new LinkedHashMap<>(fields);
        noHash.remove("hash");
        assertFalse(c.verifyHash(noHash), "missing hash must be rejected");
    }

    @Test
    void parseStatus_readsPaidMessage_andPaidRequiresValidHash() {
        PaynowClient c = client("secret-key");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("reference", "01INTENT");
        fields.put("paynowreference", "PN-778");
        fields.put("amount", "43.14");
        fields.put("status", "Paid");
        fields.put("hash", c.hash(fields.values()));
        String raw = PaynowClient.encodeForm(fields);

        PaynowClient.StatusResult status = c.parseStatus(raw);
        assertTrue(status.hashValid());
        assertTrue(status.paid());
        assertEquals("01INTENT", status.reference());
        assertEquals(new BigDecimal("43.14"), status.amount());

        // Same message verified with the WRONG key: hash invalid → never "paid".
        PaynowClient other = client("other-key");
        PaynowClient.StatusResult forged = other.parseStatus(raw);
        assertFalse(forged.hashValid());
        assertFalse(forged.paid(), "a Paid status without a valid hash must not settle");
    }

    @Test
    void parseForm_decodesUrlEncoding_andLowercasesKeys() {
        Map<String, String> parsed = PaynowClient.parseForm(
                "Status=Ok&BrowserUrl=https%3A%2F%2Fwww.paynow.co.zw%2Fpay%2F1&PollUrl=https%3A%2F%2Fpoll");
        assertEquals("Ok", parsed.get("status"));
        assertEquals("https://www.paynow.co.zw/pay/1", parsed.get("browserurl"));
        assertEquals("https://poll", parsed.get("pollurl"));
    }

    @Test
    void cancelledAndInterimStatuses_mapHonestly() {
        PaynowClient c = client("secret-key");
        Map<String, String> cancelled = new LinkedHashMap<>();
        cancelled.put("reference", "01INTENT");
        cancelled.put("status", "Cancelled");
        cancelled.put("hash", c.hash(cancelled.values()));
        assertTrue(c.parseStatus(PaynowClient.encodeForm(cancelled)).cancelled());

        Map<String, String> created = new LinkedHashMap<>();
        created.put("reference", "01INTENT");
        created.put("status", "Created");
        created.put("hash", c.hash(created.values()));
        PaynowClient.StatusResult interim = c.parseStatus(PaynowClient.encodeForm(created));
        assertFalse(interim.paid());
        assertFalse(interim.cancelled());
    }
}
