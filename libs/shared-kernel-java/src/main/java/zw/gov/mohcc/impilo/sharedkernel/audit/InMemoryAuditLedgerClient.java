package zw.gov.mohcc.impilo.sharedkernel.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * In-memory implementation of {@link AuditLedgerClient} for unit/integration tests.
 * Thread-safe via synchronized list.
 */
public final class InMemoryAuditLedgerClient implements AuditLedgerClient {

    private final List<AuditRecord> records = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void append(AuditRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        records.add(record);
    }

    /** Return an unmodifiable snapshot of all appended records. */
    public List<AuditRecord> getRecords() {
        synchronized (records) {
            return List.copyOf(records);
        }
    }

    /** Number of records appended. */
    public int size() {
        return records.size();
    }

    /** Clear all records (for test reset). */
    public void clear() {
        records.clear();
    }
}
