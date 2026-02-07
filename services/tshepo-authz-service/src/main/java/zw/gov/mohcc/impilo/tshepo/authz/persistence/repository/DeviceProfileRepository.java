package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.DeviceProfileEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceProfileRepository extends JpaRepository<DeviceProfileEntity, Long> {

    /**
     * Lookup a device profile by tenant and fingerprint (unique constraint).
     */
    Optional<DeviceProfileEntity> findByTenantIdAndFingerprint(UUID tenantId, String fingerprint);
}
