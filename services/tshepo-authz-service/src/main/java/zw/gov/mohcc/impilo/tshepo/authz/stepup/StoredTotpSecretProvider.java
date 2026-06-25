package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.TotpEnrolmentRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Real {@link StepUpProviders.TotpSecretProvider} backed by the enrolment store: returns the
 * decrypted shared secret for an actor with an ACTIVE enrolment, or empty otherwise (fail-closed
 * per actor — un-enrolled actors cannot complete TOTP step-up).
 *
 * <p>Registered only when {@code tshepo.authz.totp.enrolment-enabled=true}; otherwise the
 * {@code @ConditionalOnMissingBean} fail-closed default in {@code StepUpProvidersConfig} stays.</p>
 */
@Component
@ConditionalOnProperty(name = "tshepo.authz.totp.enrolment-enabled", havingValue = "true")
public class StoredTotpSecretProvider implements StepUpProviders.TotpSecretProvider {

    private final TotpEnrolmentRepository repository;
    private final TotpSecretCipher cipher;

    public StoredTotpSecretProvider(TotpEnrolmentRepository repository, TotpSecretCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    @Override
    public Optional<byte[]> secretFor(UUID tenantId, String actorId) {
        return repository.findByTenantIdAndActorIdAndStatus(tenantId, actorId, "ACTIVE")
                .map(e -> cipher.decrypt(e.getSecretCipher(), e.getSecretIv()));
    }
}
