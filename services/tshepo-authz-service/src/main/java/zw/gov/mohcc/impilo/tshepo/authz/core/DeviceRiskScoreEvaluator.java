package zw.gov.mohcc.impilo.tshepo.authz.core;

import java.util.UUID;

/**
 * Device / session risk score used by {@link PolicyEngine} step 1.
 * Extracted as an interface so unit tests can stub scoring without mocking
 * concrete {@link RiskScoring} (problematic on some JDK / Mockito combinations).
 */
public interface DeviceRiskScoreEvaluator {

    int score(UUID tenantId, String fingerprint, String actorId);
}
