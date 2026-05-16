package zw.gov.mohcc.impilo.pct.core.telemedicine;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default managed provider for vNext virtual sessions.
 */
@Component
public class ManagedPrimarySessionProvider implements TelemedicineSessionProvider {

    @Override
    public String providerType() {
        return "MANAGED_PRIMARY";
    }

    @Override
    public SessionProvisioningResult provision(SessionProvisioningRequest request) {
        String room = "https://telecare.impilo/room/" + UUID.randomUUID();
        String token = UUID.randomUUID().toString().replace("-", "");
        return new SessionProvisioningResult(providerType(), "managed-primary", room, token);
    }
}
