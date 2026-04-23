package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricProfileEntity;

import java.util.Optional;
import java.util.UUID;

public interface BiometricProfileRepository extends JpaRepository<BiometricProfileEntity, UUID> {

    Optional<BiometricProfileEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId);
}
