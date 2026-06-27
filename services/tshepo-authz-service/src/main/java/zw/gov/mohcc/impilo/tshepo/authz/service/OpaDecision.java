package zw.gov.mohcc.impilo.tshepo.authz.service;

import java.util.List;

/** The OPA {@code impilo.authz.decision} verdict for the gate sub-decision (Phase 3 strangler). */
public record OpaDecision(boolean allow, List<String> denyReasons) {
}
