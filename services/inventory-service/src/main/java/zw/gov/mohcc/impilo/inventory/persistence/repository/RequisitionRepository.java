package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.RequisitionStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RequisitionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequisitionRepository extends JpaRepository<RequisitionEntity, UUID> {

    List<RequisitionEntity> findByFromStoreIdAndStatus(UUID fromStoreId, RequisitionStatus status);

    List<RequisitionEntity> findByToStoreIdAndStatus(UUID toStoreId, RequisitionStatus status);

    Page<RequisitionEntity> findByTenantIdAndStatus(UUID tenantId, RequisitionStatus status, Pageable pageable);
}
