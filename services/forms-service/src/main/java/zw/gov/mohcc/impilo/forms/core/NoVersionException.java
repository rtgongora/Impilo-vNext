package zw.gov.mohcc.impilo.forms.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class NoVersionException extends RuntimeException {
    public NoVersionException(String formId) {
        super("No versions exist for form: " + formId + ". Create a version before publishing.");
    }
}
