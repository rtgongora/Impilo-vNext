package zw.gov.mohcc.impilo.pharmacy.elmis.core;

/**
 * Thrown when a dispense sync record is not found.
 */
public class SyncNotFoundException extends RuntimeException {

    public SyncNotFoundException(Long id) {
        super("Dispense sync record not found: " + id);
    }
}
