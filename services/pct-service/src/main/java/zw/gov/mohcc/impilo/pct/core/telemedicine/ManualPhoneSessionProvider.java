package zw.gov.mohcc.impilo.pct.core.telemedicine;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Manual phone/documented fallback pathway when managed media is unavailable.
 */
@Component
public class ManualPhoneSessionProvider implements TelemedicineSessionProvider {

    @Override
    public String providerType() {
        return "MANUAL_PHONE";
    }

    @Override
    public SessionProvisioningResult provision(SessionProvisioningRequest request) {
        String callRef = "phone://teleconsult/" + UUID.randomUUID();
        String token = UUID.randomUUID().toString().replace("-", "");
        return new SessionProvisioningResult(providerType(), "manual-phone", callRef, token);
    }
}
