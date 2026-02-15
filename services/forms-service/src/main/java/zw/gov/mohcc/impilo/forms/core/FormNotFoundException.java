package zw.gov.mohcc.impilo.forms.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class FormNotFoundException extends RuntimeException {
    public FormNotFoundException(String formId) {
        super("Form not found: " + formId);
    }
}
