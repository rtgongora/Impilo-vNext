package zw.gov.mohcc.impilo.landela.core;

/**
 * Exception thrown when a storage operation (MinIO or Landela DMS) fails.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
