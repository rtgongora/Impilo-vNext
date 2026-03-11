package zw.gov.mohcc.impilo.datagovernance.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.datagovernance.domain.PolicyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<PolicyEntity, UUID> {

    List<PolicyEntity> findByTenantId(UUID tenantId);

    Page<PolicyEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<PolicyEntity> findByTenantIdAndNameAndVersion(UUID tenantId, String name, int version);

    @Query("SELECT MAX(p.version) FROM PolicyEntity p WHERE p.tenantId = :tenantId AND p.name = :name")
    Optional<Integer> findMaxVersion(@Param("tenantId") UUID tenantId, @Param("name") String name);

    @Query("SELECT p FROM PolicyEntity p WHERE p.tenantId = :tenantId AND p.name = :name AND p.status = 'PUBLISHED' ORDER BY p.version DESC")
    List<PolicyEntity> findPublishedByName(@Param("tenantId") UUID tenantId, @Param("name") String name);
}
