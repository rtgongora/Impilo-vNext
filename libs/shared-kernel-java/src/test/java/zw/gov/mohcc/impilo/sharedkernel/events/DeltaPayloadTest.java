package zw.gov.mohcc.impilo.sharedkernel.events;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeltaPayloadTest {

    @Test
    void createDeltaIsValid() {
        DeltaPayload delta = new DeltaPayload(
                "CREATE", null, Map.of("name", "John"), List.of("name"));
        assertEquals("CREATE", delta.op());
        assertNull(delta.before());
        assertNotNull(delta.after());
        assertEquals(List.of("name"), delta.changedFields());
    }

    @Test
    void invalidOpThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeltaPayload("INVALID", null, Map.of(), List.of()));
    }

    @Test
    void nullOpThrows() {
        assertThrows(NullPointerException.class,
                () -> new DeltaPayload(null, null, Map.of(), List.of()));
    }

    @Test
    void nullChangedFieldsThrows() {
        assertThrows(NullPointerException.class,
                () -> new DeltaPayload("UPDATE", Map.of(), Map.of(), null));
    }

    @Test
    void allValidOpsAccepted() {
        for (String op : List.of("CREATE", "UPDATE", "DELETE", "MERGE", "REVOKE")) {
            assertDoesNotThrow(() -> new DeltaPayload(op, null, Map.of(), List.of()));
        }
    }

    @Test
    void changedFieldsIsImmutable() {
        DeltaPayload delta = new DeltaPayload("UPDATE", null, Map.of(), List.of("a", "b"));
        assertThrows(UnsupportedOperationException.class,
                () -> delta.changedFields().add("c"));
    }
}
