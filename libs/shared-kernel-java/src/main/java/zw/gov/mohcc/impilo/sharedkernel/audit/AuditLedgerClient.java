package zw.gov.mohcc.impilo.sharedkernel.audit;

/**
 * Contract for appending audit records to the immutable audit ledger.
 * Implementations may write to PostgreSQL, Kafka, or an in-memory store (for tests).
 */
public interface AuditLedgerClient {

    /**
     * Append an audit record to the ledger.
     *
     * @param record the audit record to persist
     */
    void append(AuditRecord record);
}
