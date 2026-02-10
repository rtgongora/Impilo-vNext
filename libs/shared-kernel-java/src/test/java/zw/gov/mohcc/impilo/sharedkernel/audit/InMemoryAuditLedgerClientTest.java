package zw.gov.mohcc.impilo.sharedkernel.audit;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAuditLedgerClientTest {

    private AuditRecord sampleRecord() {
        return new AuditRecord(
                "user-42",
                "USER",
                "PATIENT_MERGE",
                "ALLOW",
                List.of("POLICY_V2_MATCH"),
                "v2.1",
                "tenant-zw",
                "pod-harare",
                "corr-001",
                OffsetDateTime.now(),
                Map.of("cpid", "CPID-123")
        );
    }

    @Test
    void appendAndRetrieve() {
        InMemoryAuditLedgerClient client = new InMemoryAuditLedgerClient();
        AuditRecord record = sampleRecord();
        client.append(record);
        assertEquals(1, client.size());
        assertEquals(record, client.getRecords().get(0));
    }

    @Test
    void clearResetsState() {
        InMemoryAuditLedgerClient client = new InMemoryAuditLedgerClient();
        client.append(sampleRecord());
        client.append(sampleRecord());
        assertEquals(2, client.size());
        client.clear();
        assertEquals(0, client.size());
    }

    @Test
    void nullRecordThrows() {
        InMemoryAuditLedgerClient client = new InMemoryAuditLedgerClient();
        assertThrows(NullPointerException.class, () -> client.append(null));
    }

    @Test
    void getRecordsReturnsImmutableSnapshot() {
        InMemoryAuditLedgerClient client = new InMemoryAuditLedgerClient();
        client.append(sampleRecord());
        List<AuditRecord> snapshot = client.getRecords();
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(sampleRecord()));
    }
}
