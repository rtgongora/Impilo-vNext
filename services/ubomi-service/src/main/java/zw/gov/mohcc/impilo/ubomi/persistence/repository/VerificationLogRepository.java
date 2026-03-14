package zw.gov.mohcc.impilo.ubomi.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.VerificationLogEntity;

import java.util.UUID;

@Repository
public interface VerificationLogRepository extends JpaRepository<VerificationLogEntity, Long> {

    Page<VerificationLogEntity> findByTenantIdAndRegistrationNumber(UUID tenantId, String registrationNumber, Pageable pageable);

    Page<VerificationLogEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
