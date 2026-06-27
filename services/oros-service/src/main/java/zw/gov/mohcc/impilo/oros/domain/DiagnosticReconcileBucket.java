package zw.gov.mohcc.impilo.oros.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState.*;

/**
 * Operational reconciliation buckets for the diagnostics journey (spec §H) — "stuck order"
 * cohorts defined by the fine-grained imaging state an order is dwelling in.
 *
 * <p>{@code CRITICAL_UNACKNOWLEDGED} is result-driven (critical results without acknowledgement)
 * and is handled separately from these imaging-state buckets.</p>
 */
public enum DiagnosticReconcileBucket {
    RECEIVED_NOT_ACCEPTED(EnumSet.of(RECEIVED)),
    ACCEPTED_NOT_SCHEDULED(EnumSet.of(ACCEPTED)),
    SCHEDULED_NO_SHOW(EnumSet.of(NO_SHOW)),
    PERFORMED_NOT_REPORTED(EnumSet.of(PERFORMED, AWAITING_IMAGES, AWAITING_REPORT)),
    IMAGES_LINKED_REPORT_PENDING(EnumSet.of(IMAGES_LINKED, AWAITING_REPORT)),
    REPORTED_NOT_RELEASED(EnumSet.of(PRELIMINARY_REPORT, FINAL_REPORT)),
    RELEASED_NOT_ACKNOWLEDGED(EnumSet.of(RELEASED));

    private final Set<ImagingWorkflowState> states;

    DiagnosticReconcileBucket(Set<ImagingWorkflowState> states) {
        this.states = Collections.unmodifiableSet(states);
    }

    public Set<ImagingWorkflowState> states() {
        return states;
    }
}
