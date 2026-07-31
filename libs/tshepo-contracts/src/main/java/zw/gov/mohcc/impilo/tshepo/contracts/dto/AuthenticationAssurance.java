package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Server-derived authentication strength. It is deliberately separate from identity IAL/LoA. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthenticationAssurance(
        int aal,
        List<String> methods,
        Instant authenticationTime,
        Instant stepUpTime,
        boolean phishingResistant,
        String sessionId,
        String flowId
) {
    public AuthenticationAssurance {
        if (aal < 0 || aal > 3) throw new IllegalArgumentException("aal must be between 0 and 3");
        methods = methods == null ? List.of() : List.copyOf(methods);
    }

    public static AuthenticationAssurance none() {
        return new AuthenticationAssurance(0, List.of(), null, null, false, null, null);
    }

    public boolean isFresh(int maxAgeSeconds, Instant now) {
        Instant reference = stepUpTime != null ? stepUpTime : authenticationTime;
        return reference != null && !reference.plusSeconds(maxAgeSeconds).isBefore(now);
    }
}
