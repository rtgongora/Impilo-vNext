package zw.gov.mohcc.impilo.mvumo.domain;

/**
 * Adaptive consent assurance levels (0–4). Minimum required per workflow; actual method may be higher.
 */
public enum ConsentAssuranceLevel {
    L0_RECORDED_ADMIN,
    L1_SIMPLE_DIGITAL,
    L2_VERIFIED,
    L3_WITNESSED_HIGH_ASSURANCE,
    L4_EMERGENCY_OVERRIDE
}
