package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonorProfileEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonorProfileRepository extends JpaRepository<DonorProfileEntity, Long> {
    Optional<DonorProfileEntity> findByDonorIdAndTenantId(UUID donorId, UUID tenantId);
    Optional<DonorProfileEntity> findByPersonCpidAndTenantId(String personCpid, UUID tenantId);
    List<DonorProfileEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
