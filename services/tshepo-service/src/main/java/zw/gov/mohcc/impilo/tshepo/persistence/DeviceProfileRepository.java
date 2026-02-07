package zw.gov.mohcc.impilo.tshepo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceProfileRepository extends JpaRepository<DeviceProfileEntity, Long> {

    Optional<DeviceProfileEntity> findByTenantIdAndFingerprint(UUID tenantId, String fingerprint);
}
