package zw.gov.mohcc.impilo.tshepo.keys.config;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.security.Security;

/**
 * Registers Bouncy Castle as a JCA Security Provider on application startup.
 *
 * <p>Required for Ed25519 key operations and PEM certificate parsing.</p>
 */
@Configuration
public class BouncyCastleConfig {

    @PostConstruct
    public void registerBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
