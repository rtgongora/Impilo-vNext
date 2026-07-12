package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.VendorStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.VendorProfileEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorProfileRepository extends JpaRepository<VendorProfileEntity, String> {
    Page<VendorProfileEntity> findByTenantIdAndStatus(UUID tenantId, VendorStatus status, Pageable pageable);
    Page<VendorProfileEntity> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<VendorProfileEntity> findByTenantIdAndActorBinding(UUID tenantId, String actorBinding);
}
