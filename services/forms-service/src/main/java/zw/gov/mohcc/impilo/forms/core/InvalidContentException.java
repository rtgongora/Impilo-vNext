package zw.gov.mohcc.impilo.forms.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidContentException extends RuntimeException {
    public InvalidContentException(String message) {
        super(message);
    }
}
