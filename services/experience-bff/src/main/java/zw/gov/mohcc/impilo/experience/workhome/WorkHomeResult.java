package zw.gov.mohcc.impilo.experience.workhome;

import java.util.List;

/**
 * Result of one Work Home composition (Phase E3).
 *
 * @param contextId     the re-proven context id, null only when {@code friendlyState} is set
 * @param family        the {@link WorkHomeFamily} name the active mode resolved to, null on failure
 * @param mode          the active work mode used to select the family
 * @param friendlyState null | work_context_unavailable | work_mode_unavailable — never enumerates
 *                      *why* to the client, matching the C3 session-mint precedent
 * @param sections      always present; empty only alongside a non-null {@code friendlyState}
 */
public record WorkHomeResult(
        String contextId,
        String family,
        String mode,
        String friendlyState,
        List<WorkHomeSection> sections
) {
    public static WorkHomeResult unavailable(String friendlyState) {
        return new WorkHomeResult(null, null, null, friendlyState, List.of());
    }
}
