package zw.gov.mohcc.impilo.inventory.elmis.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested sync state record cannot be found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class SyncNotFoundException extends RuntimeException {

    public SyncNotFoundException(Long id) {
        super("Sync state not found: " + id);
    }
}
