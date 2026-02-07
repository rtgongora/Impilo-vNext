package zw.gov.mohcc.impilo.tshepo.offline.exception;

/**
 * Thrown when an offline action is denied by the rules engine.
 */
public class OfflineActionDeniedException extends OfflineServiceException {

    public OfflineActionDeniedException(String action, String reason) {
        super("ACTION_DENIED", "Offline action '" + action + "' denied: " + reason);
    }
}
