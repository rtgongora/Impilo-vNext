package zw.gov.mohcc.impilo.inpatient.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an admission cannot be found by its reference.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AdmissionNotFoundException extends RuntimeException {

    public AdmissionNotFoundException(String message) {
        super(message);
    }
}
