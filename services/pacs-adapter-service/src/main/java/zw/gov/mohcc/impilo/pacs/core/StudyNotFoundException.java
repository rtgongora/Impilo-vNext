package zw.gov.mohcc.impilo.pacs.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an imaging study cannot be found by its ID.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class StudyNotFoundException extends RuntimeException {

    public StudyNotFoundException(String message) {
        super(message);
    }
}
