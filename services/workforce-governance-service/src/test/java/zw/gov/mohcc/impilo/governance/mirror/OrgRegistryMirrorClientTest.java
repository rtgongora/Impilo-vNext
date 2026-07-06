package zw.gov.mohcc.impilo.governance.mirror;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OrgRegistryMirrorClientTest {

    @Test
    void push_onUnreachableReceiver_returnsFalse_andNeverThrows() {
        OrgMirrorProperties props = new OrgMirrorProperties();
        // Reserved TEST-NET-1 address / closed port — connect fails fast within timeout.
        props.setBaseUrl("http://127.0.0.1:1");
        props.setConnectTimeoutMs(500);
        props.setReadTimeoutMs(500);
        OrgRegistryMirrorClient client = new OrgRegistryMirrorClient(props, new SimpleMeterRegistry());

        OrgMirrorPayload payload = new OrgMirrorPayload(
                UUID.randomUUID(), "ORG-1", "Legal", "HOSPITAL_GROUP", "ACTIVE", null);

        boolean[] result = new boolean[1];
        assertDoesNotThrow(() -> result[0] = client.push(payload, UUID.randomUUID(), UUID.randomUUID()));
        assertFalse(result[0], "unreachable receiver must yield a best-effort false, not an exception");
    }
}
