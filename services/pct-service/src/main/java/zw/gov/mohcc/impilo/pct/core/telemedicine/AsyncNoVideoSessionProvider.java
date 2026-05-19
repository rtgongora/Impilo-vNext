package zw.gov.mohcc.impilo.pct.core.telemedicine;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Asynchronous specialist-review mode with no real-time media engine dependency.
 */
@Component
public class AsyncNoVideoSessionProvider implements TelemedicineSessionProvider {

    @Override
    public String providerType() {
        return "ASYNC_NO_VIDEO";
    }

    @Override
    public SessionProvisioningResult provision(SessionProvisioningRequest request) {
        String thread = "async://teleconsult/" + UUID.randomUUID();
        String token = UUID.randomUUID().toString().replace("-", "");
        return new SessionProvisioningResult(providerType(), "async-no-video", thread, token);
    }
}
