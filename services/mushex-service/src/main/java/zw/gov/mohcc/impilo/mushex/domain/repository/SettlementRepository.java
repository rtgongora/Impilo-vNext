package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.SettlementEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.SettlementStatus;

import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<SettlementEntity, String> {

    Page<SettlementEntity> findByTenantIdAndStatus(UUID tenantId, SettlementStatus status, Pageable pageable);
}
