package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.ClaimStatus;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimRepository extends JpaRepository<ClaimEntity, String> {

    Page<ClaimEntity> findByTenantIdAndStatus(UUID tenantId, ClaimStatus status, Pageable pageable);

    Optional<ClaimEntity> findByBillId(String billId);

    Page<ClaimEntity> findByTenantIdAndInsurerId(UUID tenantId, String insurerId, Pageable pageable);
}
