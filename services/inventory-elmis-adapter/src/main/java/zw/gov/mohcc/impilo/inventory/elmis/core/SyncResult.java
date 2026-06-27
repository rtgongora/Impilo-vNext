package zw.gov.mohcc.impilo.inventory.elmis.core;

/**
 * Terminal outcome of an eLMIS sync attempt. A sync always ends COMPLETED or
 * FAILED — never stuck IN_PROGRESS (G023).
 */
public record SyncResult(String status, int recordsSynced, String message) {

    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    public static SyncResult completed(int recordsSynced) {
        return new SyncResult(COMPLETED, recordsSynced, null);
    }

    public static SyncResult notConfigured() {
        return new SyncResult(FAILED, 0,
                "NOT_LIVE: eLMIS endpoint not configured — set elmis.connector.external-url "
                        + "to a real national eLMIS endpoint to enable synchronisation");
    }

    public static SyncResult failed(String message) {
        return new SyncResult(FAILED, 0, message);
    }
}
