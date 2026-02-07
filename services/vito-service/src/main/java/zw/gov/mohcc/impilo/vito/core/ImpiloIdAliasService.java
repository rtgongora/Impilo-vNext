package zw.gov.mohcc.impilo.vito.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.vito.persistence.entity.IdentityAliasEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.IdentityAliasRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Impilo ID Alias Service — the core identity-resolution engine.
 *
 * Implements the double-hash Identity Alias Pattern:
 *   1. lookup_hash = HMAC-SHA256(pepper, normalize(id))  → fast O(1) lookup
 *   2. verifier    = Argon2id(normalize(id))              → cryptographic proof
 *
 * No plaintext identity is ever stored. The lookup_hash is deterministic
 * (same input → same hash) for indexed lookup. The verifier is non-deterministic
 * (random salt each time) and used for verification only.
 *
 * VITO-specific Argon2id parameters (per Architect directive):
 *   m=64MB (65536 KB), t=3, p=4
 *
 * Thread-safe: all crypto operations are instance-safe.
 */
@Service
public class ImpiloIdAliasService {

    private final HmacService hmacService;
    private final Argon2IdService argon2IdService;
    private final IdentityAliasRepository aliasRepository;

    public ImpiloIdAliasService(HmacService hmacService,
                                 Argon2IdService argon2IdService,
                                 IdentityAliasRepository aliasRepository) {
        this.hmacService = hmacService;
        this.argon2IdService = argon2IdService;
        this.aliasRepository = aliasRepository;
    }

    /**
     * Issue a new identity alias (e.g., IMPILO_ID, NATIONAL_ID_LINK).
     *
     * @param tenantId  the tenant context
     * @param healthId  the client's health_id UUID
     * @param aliasType the type of alias (IMPILO_ID, NATIONAL_ID_LINK, etc.)
     * @param rawValue  the raw identity value (will be normalized, never stored)
     * @return the created alias entity
     */
    @Transactional
    public IdentityAliasEntity issueAlias(UUID tenantId, UUID healthId,
                                           String aliasType, String rawValue) {
        String normalized = normalize(rawValue);

        // Check for existing active alias of same type
        String lookupHash = hmacService.computeLookupHash(normalized);
        Optional<IdentityAliasEntity> existing = aliasRepository
                .findByTenantIdAndAliasTypeAndLookupHashAndStatus(tenantId, aliasType, lookupHash, "ACTIVE");

        if (existing.isPresent()) {
            throw new IllegalStateException("Active alias of type " + aliasType + " already exists");
        }

        IdentityAliasEntity alias = new IdentityAliasEntity();
        alias.setTenantId(tenantId);
        alias.setHealthId(healthId);
        alias.setAliasType(aliasType);
        alias.setLookupHash(lookupHash);
        alias.setVerifier(argon2IdService.hash(normalized));
        alias.setStatus("ACTIVE");

        return aliasRepository.save(alias);
    }

    /**
     * Resolve a client health_id by alias lookup.
     * Uses HMAC for fast O(1) indexed lookup, then Argon2id for verification.
     *
     * @return the health_id UUID if found and verified, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<UUID> resolve(UUID tenantId, String aliasType, String rawValue) {
        String normalized = normalize(rawValue);
        String lookupHash = hmacService.computeLookupHash(normalized);

        return aliasRepository
                .findByTenantIdAndAliasTypeAndLookupHashAndStatus(tenantId, aliasType, lookupHash, "ACTIVE")
                .filter(alias -> argon2IdService.verify(normalized, alias.getVerifier()))
                .map(IdentityAliasEntity::getHealthId);
    }

    /**
     * Revoke all active aliases for a client (e.g., during merge or deactivation).
     */
    @Transactional
    public void revokeAll(UUID tenantId, UUID healthId) {
        List<IdentityAliasEntity> activeAliases = aliasRepository
                .findByTenantIdAndHealthIdAndStatus(tenantId, healthId, "ACTIVE");

        for (IdentityAliasEntity alias : activeAliases) {
            alias.setStatus("REVOKED");
            alias.setRevokedAt(OffsetDateTime.now());
        }
        aliasRepository.saveAll(activeAliases);
    }

    /**
     * Rotate an alias (revoke old, issue new with same type).
     */
    @Transactional
    public IdentityAliasEntity rotateAlias(UUID tenantId, UUID healthId,
                                            String aliasType, String newRawValue) {
        // Revoke existing alias of this type
        List<IdentityAliasEntity> existing = aliasRepository
                .findByTenantIdAndHealthIdAndStatus(tenantId, healthId, "ACTIVE");

        for (IdentityAliasEntity alias : existing) {
            if (alias.getAliasType().equals(aliasType)) {
                alias.setStatus("ROTATED");
                alias.setRevokedAt(OffsetDateTime.now());
            }
        }
        aliasRepository.saveAll(existing);

        // Issue new alias
        return issueAlias(tenantId, healthId, aliasType, newRawValue);
    }

    /**
     * Normalize identity values for consistent hashing.
     * Lowercase, trim, remove non-alphanumeric separators.
     */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Identity value must not be null or blank");
        }
        return value.strip().toLowerCase().replaceAll("[\\s\\-]", "");
    }
}
