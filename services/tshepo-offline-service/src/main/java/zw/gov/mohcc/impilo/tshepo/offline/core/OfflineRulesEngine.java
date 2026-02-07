package zw.gov.mohcc.impilo.tshepo.offline.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.tshepo.offline.config.OfflineProperties;
import zw.gov.mohcc.impilo.tshepo.offline.exception.OfflineActionDeniedException;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.OfflineActionLogRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Rules engine that determines what can and cannot be done offline.
 *
 * <h3>Offline action rules:</h3>
 * <ul>
 *     <li>READ operations: allowed with valid capability token</li>
 *     <li>CREATE provisional encounter: allowed, generates O-CPID if needed</li>
 *     <li>CREATE provisional registration: allowed, generates O-CPID</li>
 *     <li>UPDATE existing records: NOT allowed offline</li>
 *     <li>DELETE: NEVER allowed offline</li>
 *     <li>DISPENSE medication: allowed with pharmacy capability</li>
 * </ul>
 *
 * <p>All offline actions are validated against the capabilities in the token
 * and logged in the offline_action_log table.</p>
 */
@Service
public class OfflineRulesEngine {

    private static final Logger log = LoggerFactory.getLogger(OfflineRulesEngine.class);

    /** Actions that are categorically forbidden offline regardless of capabilities. */
    private static final Set<String> ALWAYS_DENIED_ACTIONS = Set.of(
            "DELETE",
            "DELETE_PATIENT",
            "DELETE_ENCOUNTER",
            "DELETE_MEDICATION",
            "UPDATE_PATIENT",
            "UPDATE_ENCOUNTER",
            "UPDATE_MEDICATION",
            "MERGE_PATIENT",
            "BULK_EXPORT",
            "ADMIN_OVERRIDE"
    );

    /** Actions that require specific capabilities to be present in the token. */
    private static final Set<String> READ_ACTIONS = Set.of(
            "READ_PATIENT",
            "READ_MEDICATION",
            "READ_ENCOUNTER",
            "READ_OBSERVATION"
    );

    private static final Set<String> CREATE_ACTIONS = Set.of(
            "CREATE_PROVISIONAL_ENCOUNTER",
            "CREATE_PROVISIONAL_REGISTRATION"
    );

    private final OfflineProperties offlineProperties;
    private final OfflineActionLogRepository actionLogRepo;

    public OfflineRulesEngine(OfflineProperties offlineProperties,
                               OfflineActionLogRepository actionLogRepo) {
        this.offlineProperties = offlineProperties;
        this.actionLogRepo = actionLogRepo;
    }

    /**
     * Evaluate whether a given action is permitted offline.
     *
     * @param actionType    the action being attempted
     * @param capabilities  the capabilities present in the actor's token
     * @param tokenId       the capability token ID (for encounter limit checks)
     * @throws OfflineActionDeniedException if the action is not permitted
     */
    public void evaluateAction(String actionType, List<String> capabilities, UUID tokenId) {
        log.debug("Evaluating offline action: type={}, capabilities={}", actionType, capabilities);

        // Rule 1: Always-denied actions
        if (ALWAYS_DENIED_ACTIONS.contains(actionType)) {
            throw new OfflineActionDeniedException(actionType,
                    "This action is never permitted in offline mode");
        }

        // Rule 2: Action must match a capability in the token
        if (!isActionCoveredByCapabilities(actionType, capabilities)) {
            throw new OfflineActionDeniedException(actionType,
                    "The capability token does not include permission for this action");
        }

        // Rule 3: Encounter creation limit
        if ("CREATE_PROVISIONAL_ENCOUNTER".equals(actionType)) {
            long encounterCount = actionLogRepo.countProvisionalEncountersByToken(tokenId);
            if (encounterCount >= offlineProperties.maxOfflineEncounters()) {
                throw new OfflineActionDeniedException(actionType,
                        "Maximum offline encounters (" + offlineProperties.maxOfflineEncounters() + ") reached for this token");
            }
        }

        // Rule 4: DISPENSE_MEDICATION requires explicit pharmacy capability
        if ("DISPENSE_MEDICATION".equals(actionType) && !capabilities.contains("DISPENSE_MEDICATION")) {
            throw new OfflineActionDeniedException(actionType,
                    "DISPENSE_MEDICATION requires the pharmacy capability");
        }

        log.debug("Action {} permitted offline", actionType);
    }

    /**
     * Check whether an action type is covered by the given capabilities.
     */
    private boolean isActionCoveredByCapabilities(String actionType, List<String> capabilities) {
        // Direct match: the action type is listed in capabilities
        if (capabilities.contains(actionType)) {
            return true;
        }

        // READ actions are covered by READ_PATIENT / READ_MEDICATION capabilities
        if (READ_ACTIONS.contains(actionType)) {
            // READ_PATIENT covers READ_PATIENT, READ_ENCOUNTER, READ_OBSERVATION
            if ("READ_PATIENT".equals(actionType) || "READ_ENCOUNTER".equals(actionType)
                    || "READ_OBSERVATION".equals(actionType)) {
                return capabilities.contains("READ_PATIENT");
            }
            if ("READ_MEDICATION".equals(actionType)) {
                return capabilities.contains("READ_MEDICATION");
            }
        }

        // CREATE actions
        if (CREATE_ACTIONS.contains(actionType)) {
            if ("CREATE_PROVISIONAL_ENCOUNTER".equals(actionType)) {
                return capabilities.contains("CREATE_PROVISIONAL_ENCOUNTER");
            }
            if ("CREATE_PROVISIONAL_REGISTRATION".equals(actionType)) {
                return capabilities.contains("CREATE_PROVISIONAL_REGISTRATION");
            }
        }

        return false;
    }

    /**
     * Determine if the given action type requires O-CPID issuance.
     *
     * @param actionType the action to check
     * @return true if an O-CPID should be issued for this action
     */
    public boolean requiresOCpid(String actionType) {
        return "CREATE_PROVISIONAL_REGISTRATION".equals(actionType);
    }

    /**
     * Determine if the given action type may optionally use an O-CPID.
     *
     * @param actionType the action to check
     * @return true if this action may reference an O-CPID
     */
    public boolean mayReferenceOCpid(String actionType) {
        return "CREATE_PROVISIONAL_ENCOUNTER".equals(actionType)
                || "CREATE_PROVISIONAL_REGISTRATION".equals(actionType)
                || "DISPENSE_MEDICATION".equals(actionType);
    }

    /**
     * Get the complete list of allowed offline action types.
     */
    public List<String> getAllowedActions() {
        return offlineProperties.allowedOfflineActions();
    }
}
