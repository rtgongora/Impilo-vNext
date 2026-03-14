package zw.gov.mohcc.impilo.offlinesync.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a sync pack cannot be found by its ID.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class SyncPackNotFoundException extends RuntimeException {

    public SyncPackNotFoundException(String message) {
        super(message);
    }
}
