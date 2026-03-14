package zw.gov.mohcc.impilo.forms.api.dto;

import java.util.List;

public record ValidationResponse(
        boolean valid,
        List<String> errors
) {
}
