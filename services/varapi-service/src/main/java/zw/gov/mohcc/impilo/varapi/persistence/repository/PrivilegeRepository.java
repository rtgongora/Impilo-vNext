package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.PrivilegeEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrivilegeRepository extends JpaRepository<PrivilegeEntity, Long> {

    List<PrivilegeEntity> findByProviderId(Long providerId);

    List<PrivilegeEntity> findByProviderIdAndStatus(Long providerId, String status);

    List<PrivilegeEntity> findByProviderIdAndFacilityIdAndStatus(Long providerId, Long facilityId, String status);

    Page<PrivilegeEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
}
