package zw.gov.mohcc.impilo.patientsafety.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.patientsafety.core.IdempotencyService;

import java.util.function.Supplier;

/**
 * Replays a prior response when a mutating request carries an already-seen {@code Idempotency-Key},
 * so retries do not create duplicate reports/cases. When the key is absent the action simply runs.
 */
@Component
public class IdempotencySupport {

    private final IdempotencyService idempotency;
    private final ObjectMapper mapper;

    public IdempotencySupport(IdempotencyService idempotency, ObjectMapper mapper) {
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    public <T> ResponseEntity<?> run(RequestContext ctx, String idempotencyKey, int successStatus,
                                     Supplier<T> action) {
        String tenant = ctx.tenantId().toString();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotency.find(tenant, ctx.podId(), idempotencyKey);
            if (existing.isPresent()) {
                JsonNode body = readTree(existing.get().getResponseBody());
                return ResponseEntity.status(existing.get().getResponseStatus()).body(body);
            }
        }
        T result = action.get();
        String body = write(result);
        idempotency.record(tenant, ctx.podId(), idempotencyKey, "", successStatus, body);
        return ResponseEntity.status(successStatus).body(result);
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
