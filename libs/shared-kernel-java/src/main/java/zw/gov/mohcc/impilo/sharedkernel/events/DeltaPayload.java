package zw.gov.mohcc.impilo.sharedkernel.events;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the change delta carried inside an {@link EventEnvelope} payload.
 * Used when the event describes a state mutation (create, update, delete, merge, revoke).
 *
 * @param op            the operation type (CREATE, UPDATE, DELETE, MERGE, REVOKE)
 * @param before        state before the change (null for CREATE)
 * @param after         state after the change (null for DELETE)
 * @param changedFields list of field paths that changed
 */
public record DeltaPayload(
        String op,
        Map<String, Object> before,
        Map<String, Object> after,
        List<String> changedFields
) {

    private static final List<String> VALID_OPS = List.of(
            "CREATE", "UPDATE", "DELETE", "MERGE", "REVOKE"
    );

    public DeltaPayload {
        Objects.requireNonNull(op, "op is required");
        if (!VALID_OPS.contains(op)) {
            throw new IllegalArgumentException(
                    "Invalid op '" + op + "'; must be one of " + VALID_OPS);
        }
        Objects.requireNonNull(changedFields, "changed_fields is required");
        changedFields = List.copyOf(changedFields);
        before = before != null ? Map.copyOf(before) : null;
        after = after != null ? Map.copyOf(after) : null;
    }
}
