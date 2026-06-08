package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodBankEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodBankRepository extends JpaRepository<BloodBankEntity, Long> {
    Optional<BloodBankEntity> findByBloodBankIdAndTenantId(UUID bloodBankId, UUID tenantId);
    List<BloodBankEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
