package zw.gov.mohcc.impilo.dags.core;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Stubbed enforcement — issues a "permit token" string.
 * In production this would integrate with policy evaluation and
 * cryptographic token issuance.
 */
@Service
public class EnforcementService {

    public String issuePermitToken(Long requestId, UUID tenantId, String requesterId) {
        return "permit-token:" + tenantId + ":" + requesterId + ":" + requestId + ":" + UUID.randomUUID();
    }
}
