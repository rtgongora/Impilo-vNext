package zw.gov.mohcc.impilo.companion.consistency;

/**
 * An entry in the action registry mapping a route pattern to a consistency class.
 *
 * @param pathPattern  ant-style path pattern (e.g., "/internal/v1/patients/merge")
 * @param httpMethod   HTTP method (GET, POST, etc.) or "*" for all methods
 * @param actionType   logical action name for audit/logging
 * @param consistencyClass the consistency class (A, B, or C)
 * @param breakGlassAllowed whether break-glass override is permitted when PDP is unavailable (Class A only)
 * @param stalenessThresholdMs max staleness in milliseconds (Class B only, 0 = use default)
 */
public record ActionRegistryEntry(
        String pathPattern,
        String httpMethod,
        String actionType,
        ConsistencyClass consistencyClass,
        boolean breakGlassAllowed,
        long stalenessThresholdMs
) {
    public static ActionRegistryEntry classA(String pathPattern, String httpMethod,
                                              String actionType, boolean breakGlassAllowed) {
        return new ActionRegistryEntry(pathPattern, httpMethod, actionType,
                ConsistencyClass.CLASS_A, breakGlassAllowed, 0);
    }

    public static ActionRegistryEntry classB(String pathPattern, String httpMethod,
                                              String actionType, long stalenessThresholdMs) {
        return new ActionRegistryEntry(pathPattern, httpMethod, actionType,
                ConsistencyClass.CLASS_B, false, stalenessThresholdMs);
    }

    public static ActionRegistryEntry classC(String pathPattern, String httpMethod,
                                              String actionType) {
        return new ActionRegistryEntry(pathPattern, httpMethod, actionType,
                ConsistencyClass.CLASS_C, false, 0);
    }
}
