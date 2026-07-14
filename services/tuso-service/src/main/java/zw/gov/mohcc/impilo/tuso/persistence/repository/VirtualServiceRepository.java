package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VirtualServiceRepository extends JpaRepository<VirtualServiceEntity, Long> {

    List<VirtualServiceEntity> findByTenantIdOrderByVsUidAsc(UUID tenantId);

    List<VirtualServiceEntity> findByTenantIdAndLifecycleStatusInOrderByVsUidAsc(
            UUID tenantId, List<String> lifecycleStatuses);

    Optional<VirtualServiceEntity> findByTenantIdAndVsUid(UUID tenantId, String vsUid);
}
