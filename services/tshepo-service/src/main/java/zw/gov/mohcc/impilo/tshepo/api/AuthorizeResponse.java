package zw.gov.mohcc.impilo.tshepo.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import zw.gov.mohcc.impilo.tshepo.core.Obligations;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthorizeResponse(
    String decision,
    Obligations obligations,
    String errorCode,
    String errorMessage,
    List<String> stepUpMethods
) {
    public static AuthorizeResponse allowed(Obligations obligations) {
        return new AuthorizeResponse("ALLOW", obligations, null, null, null);
    }

    public static AuthorizeResponse denied(String code, String message) {
        return new AuthorizeResponse("DENY", null, code, message, null);
    }

    public static AuthorizeResponse stepUpRequired(List<String> methods) {
        return new AuthorizeResponse("STEP_UP_REQUIRED", null, null, null, methods);
    }
}
