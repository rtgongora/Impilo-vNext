package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.HandoverStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.HandoverEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface HandoverRepository extends JpaRepository<HandoverEntity, UUID> {

    List<HandoverEntity> findByStoreIdAndStatus(UUID storeId, HandoverStatus status);

    Page<HandoverEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
