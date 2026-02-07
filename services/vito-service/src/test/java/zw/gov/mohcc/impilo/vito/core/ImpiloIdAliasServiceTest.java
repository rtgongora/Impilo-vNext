package zw.gov.mohcc.impilo.vito.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.vito.persistence.entity.IdentityAliasEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.IdentityAliasRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImpiloIdAliasServiceTest {

    @Mock private HmacService hmacService;
    @Mock private Argon2IdService argon2IdService;
    @Mock private IdentityAliasRepository aliasRepository;

    @InjectMocks private ImpiloIdAliasService aliasService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID healthId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void issueAliasStoresHashNotPlaintext() {
        String rawValue = "123456789A";
        when(hmacService.computeLookupHash(anyString())).thenReturn("hmac_hash_value");
        when(argon2IdService.hash(anyString())).thenReturn("$argon2id$verifier");
        when(aliasRepository.findByTenantIdAndAliasTypeAndLookupHashAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(aliasRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IdentityAliasEntity alias = aliasService.issueAlias(tenantId, healthId, "IMPILO_ID", rawValue);

        assertNotNull(alias);
        assertEquals("hmac_hash_value", alias.getLookupHash());
        assertEquals("$argon2id$verifier", alias.getVerifier());
        // Verify plaintext is NEVER stored
        assertNotEquals(rawValue, alias.getLookupHash());
        assertNotEquals(rawValue, alias.getVerifier());
    }

    @Test
    void resolveReturnHealthIdWhenAliasAndArgon2Match() {
        String rawValue = "123456789A";
        IdentityAliasEntity entity = new IdentityAliasEntity();
        entity.setHealthId(healthId);
        entity.setVerifier("$argon2id$verifier");

        when(hmacService.computeLookupHash(anyString())).thenReturn("hmac_hash_value");
        when(aliasRepository.findByTenantIdAndAliasTypeAndLookupHashAndStatus(
                eq(tenantId), eq("IMPILO_ID"), eq("hmac_hash_value"), eq("ACTIVE")))
                .thenReturn(Optional.of(entity));
        when(argon2IdService.verify(anyString(), eq("$argon2id$verifier"))).thenReturn(true);

        Optional<UUID> resolved = aliasService.resolve(tenantId, "IMPILO_ID", rawValue);

        assertTrue(resolved.isPresent());
        assertEquals(healthId, resolved.get());
    }

    @Test
    void resolveReturnsEmptyWhenArgon2Fails() {
        String rawValue = "123456789A";
        IdentityAliasEntity entity = new IdentityAliasEntity();
        entity.setVerifier("$argon2id$verifier");

        when(hmacService.computeLookupHash(anyString())).thenReturn("hmac_hash_value");
        when(aliasRepository.findByTenantIdAndAliasTypeAndLookupHashAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.of(entity));
        when(argon2IdService.verify(anyString(), any())).thenReturn(false);

        Optional<UUID> resolved = aliasService.resolve(tenantId, "IMPILO_ID", rawValue);

        assertTrue(resolved.isEmpty());
    }

    @Test
    void resolveReturnsEmptyWhenNoAliasFound() {
        when(hmacService.computeLookupHash(anyString())).thenReturn("hmac_hash_value");
        when(aliasRepository.findByTenantIdAndAliasTypeAndLookupHashAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        Optional<UUID> resolved = aliasService.resolve(tenantId, "IMPILO_ID", "nonexistent");

        assertTrue(resolved.isEmpty());
    }

    @Test
    void hmacProducesDifferentHashesForDifferentValues() {
        when(hmacService.computeLookupHash("value1")).thenReturn("hash1");
        when(hmacService.computeLookupHash("value2")).thenReturn("hash2");

        assertNotEquals(
                hmacService.computeLookupHash("value1"),
                hmacService.computeLookupHash("value2")
        );
    }
}
