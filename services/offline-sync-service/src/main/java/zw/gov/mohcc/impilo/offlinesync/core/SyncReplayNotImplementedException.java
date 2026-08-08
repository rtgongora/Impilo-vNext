package zw.gov.mohcc.impilo.offlinesync.core;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a sync pack replay is requested. Replay is <b>not implemented</b>: nothing in this
 * service applies a pack's payload to any downstream system of record.
 *
 * <p>Until it is, refusing is the only safe answer. The previous behaviour flipped the pack to
 * {@code SYNCED} and returned 200 — under a comment that said {@code // Simulate replay} — which
 * tells an edge device its queued clinical work has landed nationally. The device is then free to
 * discard its local copy, so a false success here is not merely misleading, it is a data-loss
 * path. The pack's status is deliberately left untouched so it stays visibly outstanding.</p>
 */
@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class SyncReplayNotImplementedException extends RuntimeException {

    public SyncReplayNotImplementedException(Long syncPackId) {
        super("Sync pack " + syncPackId + " was NOT replayed: replay is not implemented. "
                + "No payload has been applied to any system of record, and the pack remains "
                + "unsynced. Do not discard the local copy.");
    }
}
