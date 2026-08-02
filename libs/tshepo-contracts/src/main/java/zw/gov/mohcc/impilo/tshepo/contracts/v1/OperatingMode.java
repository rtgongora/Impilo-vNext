package zw.gov.mohcc.impilo.tshepo.contracts.v1;

/** Governed operating modes — none may silently fail open. */
public enum OperatingMode {
    NORMAL,
    DEGRADED,
    OFFLINE,
    RECOVERY,
    BREAK_GLASS,
    READ_ONLY_EMERGENCY
}
