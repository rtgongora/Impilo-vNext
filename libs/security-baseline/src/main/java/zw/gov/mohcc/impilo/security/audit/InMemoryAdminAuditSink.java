package zw.gov.mohcc.impilo.security.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory sink for tests and local development.
 * Records are accumulated in a thread-safe list.
 */
public final class InMemoryAdminAuditSink implements AdminAuditEmitter.AdminAuditSink {

    private final List<AdminAuditRecord> records = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void persist(AdminAuditRecord record) {
        records.add(record);
    }

    public List<AdminAuditRecord> getRecords() {
        return List.copyOf(records);
    }

    public void clear() {
        records.clear();
    }
}
