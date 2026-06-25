package zw.gov.mohcc.impilo.tshepo.authz.stepup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TotpEnrolmentEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.TotpEnrolmentRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Proves the full TOTP enrolment flow with no external dependency: enrol → derive a code from the
 * stored secret → confirm activates → {@link StoredTotpSecretProvider} returns the secret → a
 * freshly generated code verifies via the shared {@link TotpCodes}; wrong codes are rejected.
 */
@ExtendWith(MockitoExtension.class)
class TotpEnrolmentServiceTest {

    @Mock
    private TotpEnrolmentRepository repository;

    private TotpSecretCipher cipher;
    private AuthzProperties properties;
    private TotpEnrolmentService service;

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String ACTOR = "provider-9";

    @BeforeEach
    void setUp() {
        properties = new AuthzProperties();
        properties.getTotp().setEncryptionKey("a-strong-totp-encryption-key-0123456789");
        properties.getTotp().setIssuer("Impilo");
        cipher = new TotpSecretCipher(properties);
        service = new TotpEnrolmentService(repository, cipher, properties);
        org.mockito.Mockito.lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static String currentCode(byte[] secret) {
        long step = Instant.now().getEpochSecond() / 30;
        return TotpCodes.generate(secret, step, 6);
    }

    @Test
    void enrolReturnsOtpauthUriAndStoresPendingEncryptedSecret() {
        when(repository.findByTenantIdAndActorId(TENANT, ACTOR)).thenReturn(Optional.empty());

        TotpEnrolmentService.EnrolmentResult result = service.enrol(TENANT, ACTOR);

        assertThat(result.otpauthUri()).startsWith("otpauth://totp/Impilo:")
                .contains("secret=" + result.base32Secret())
                .contains("issuer=Impilo").contains("algorithm=SHA1")
                .contains("digits=6").contains("period=30");

        ArgumentCaptor<TotpEnrolmentEntity> captor = ArgumentCaptor.forClass(TotpEnrolmentEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        TotpEnrolmentEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("PENDING_CONFIRM");
        assertThat(saved.getSecretCipher()).isNotBlank();
        // The stored secret is encrypted at rest, not the raw bytes.
        assertThat(saved.getSecretCipher()).doesNotContain(result.base32Secret());
    }

    @Test
    void confirmActivatesWithAValidCodeAndProviderThenReturnsSecret() {
        when(repository.findByTenantIdAndActorId(TENANT, ACTOR)).thenReturn(Optional.empty());
        service.enrol(TENANT, ACTOR);

        ArgumentCaptor<TotpEnrolmentEntity> captor = ArgumentCaptor.forClass(TotpEnrolmentEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        TotpEnrolmentEntity enrolment = captor.getValue();
        byte[] secret = cipher.decrypt(enrolment.getSecretCipher(), enrolment.getSecretIv());

        // Confirm with a freshly derived code activates the enrolment.
        when(repository.findByTenantIdAndActorId(TENANT, ACTOR)).thenReturn(Optional.of(enrolment));
        assertThat(service.confirm(TENANT, ACTOR, currentCode(secret))).isTrue();
        assertThat(enrolment.getStatus()).isEqualTo("ACTIVE");

        // The provider returns the secret for the ACTIVE enrolment, and a new code verifies it.
        when(repository.findByTenantIdAndActorIdAndStatus(TENANT, ACTOR, "ACTIVE"))
                .thenReturn(Optional.of(enrolment));
        StoredTotpSecretProvider provider = new StoredTotpSecretProvider(repository, cipher);
        Optional<byte[]> resolved = provider.secretFor(TENANT, ACTOR);
        assertThat(resolved).isPresent();
        assertThat(TotpCodes.verify(resolved.get(), currentCode(secret), 6, 30)).isTrue();
    }

    @Test
    void confirmRejectsAWrongCode() {
        when(repository.findByTenantIdAndActorId(TENANT, ACTOR)).thenReturn(Optional.empty());
        service.enrol(TENANT, ACTOR);
        ArgumentCaptor<TotpEnrolmentEntity> captor = ArgumentCaptor.forClass(TotpEnrolmentEntity.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        TotpEnrolmentEntity enrolment = captor.getValue();
        byte[] secret = cipher.decrypt(enrolment.getSecretCipher(), enrolment.getSecretIv());

        when(repository.findByTenantIdAndActorId(TENANT, ACTOR)).thenReturn(Optional.of(enrolment));
        String valid = currentCode(secret);
        String wrong = valid.equals("000000") ? "111111" : "000000";
        assertThat(service.confirm(TENANT, ACTOR, wrong)).isFalse();
        assertThat(enrolment.getStatus()).isEqualTo("PENDING_CONFIRM");
    }

    @Test
    void providerReturnsEmptyForUnenrolledActor() {
        when(repository.findByTenantIdAndActorIdAndStatus(TENANT, ACTOR, "ACTIVE"))
                .thenReturn(Optional.empty());
        StoredTotpSecretProvider provider = new StoredTotpSecretProvider(repository, cipher);
        assertThat(provider.secretFor(TENANT, ACTOR)).isEmpty();
    }
}
