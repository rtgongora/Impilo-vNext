package zw.gov.mohcc.impilo.tshepo.identity.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.IdMappingEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdMappingRepository extends JpaRepository<IdMappingEntity, Long> {

    Optional<IdMappingEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId);

    Optional<IdMappingEntity> findByTenantIdAndCpid(UUID tenantId, UUID cpid);

    /**
     * Race-safe insert: two concurrent creators for the same (tenant, health_id)
     * resolve to one row via ON CONFLICT DO NOTHING instead of a constraint error.
     * Returns 1 if this call inserted the row, 0 if it already existed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO tshepo_identity.id_mapping
                (tenant_id, health_id, cpid, crid, mapping_status, created_at)
            VALUES (:tenantId, :healthId, :cpid, :crid, 'ACTIVE', now())
            ON CONFLICT (tenant_id, health_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("tenantId") UUID tenantId,
                       @Param("healthId") UUID healthId,
                       @Param("cpid") UUID cpid,
                       @Param("crid") UUID crid);
}
