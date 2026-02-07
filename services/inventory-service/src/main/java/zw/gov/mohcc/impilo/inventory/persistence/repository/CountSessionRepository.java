package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.CountSessionStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CountSessionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CountSessionRepository extends JpaRepository<CountSessionEntity, UUID> {

    List<CountSessionEntity> findByStoreIdAndStatus(UUID storeId, CountSessionStatus status);

    Page<CountSessionEntity> findByTenantIdAndStatus(UUID tenantId, CountSessionStatus status, Pageable pageable);
}
