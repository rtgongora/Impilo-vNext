package zw.gov.mohcc.impilo.oros.domain;

/**
 * Status of a reconciliation queue entry in hybrid adapter mode.
 *
 * <p>When operating in {@link AdapterMode#HYBRID} mode, results from
 * external systems may not directly match an existing OROS order. These
 * unmatched results enter the reconciliation queue for operator review.</p>
 *
 * <ul>
 *   <li>{@code PENDING} — awaiting operator review and matching</li>
 *   <li>{@code MATCHED} — automatically matched to an existing order
 *       based on confidence scoring</li>
 *   <li>{@code RESOLVED} — operator has manually matched or resolved</li>
 *   <li>{@code REJECTED} — operator has determined this entry is invalid
 *       or does not belong to any order</li>
 * </ul>
 */
public enum ReconcileStatus {
    PENDING,
    MATCHED,
    RESOLVED,
    REJECTED
}
