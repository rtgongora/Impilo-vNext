package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientMergeCaseEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientMergeCaseRepository extends JpaRepository<ClientMergeCaseEntity, UUID> {
    List<ClientMergeCaseEntity> findByPrimaryHealthIdOrSecondaryHealthIdOrderByCreatedAtDesc(UUID primaryHealthId,
                                                                                              UUID secondaryHealthId);

    @Query("""
            select mc
            from ClientMergeCaseEntity mc
            where mc.tenantId = :tenantId
              and (mc.primaryHealthId = :healthId or mc.secondaryHealthId = :healthId)
            order by mc.createdAt desc
            """)
    List<ClientMergeCaseEntity> findTimelineByTenantIdAndHealthId(@Param("tenantId") UUID tenantId,
                                                                  @Param("healthId") UUID healthId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
