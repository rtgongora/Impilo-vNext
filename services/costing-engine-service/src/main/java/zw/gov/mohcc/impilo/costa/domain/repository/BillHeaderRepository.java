package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BillStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillHeaderRepository extends JpaRepository<BillHeaderEntity, String> {
    Page<BillHeaderEntity> findByTenantIdAndFacilityIdAndStatus(UUID tenantId, UUID facilityId, BillStatus status, Pageable pageable);
    List<BillHeaderEntity> findByEncounterId(String encounterId);
    Optional<BillHeaderEntity> findByMsikaOrderId(String msikaOrderId);
    Page<BillHeaderEntity> findByTenantIdAndStatus(UUID tenantId, BillStatus status, Pageable pageable);
    Page<BillHeaderEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
