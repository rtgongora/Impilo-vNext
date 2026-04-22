package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientRelationshipEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientRelationshipRepository extends JpaRepository<ClientRelationshipEntity, UUID> {
    List<ClientRelationshipEntity> findByClientHealthIdOrRelatedClientHealthIdOrderByCreatedAtDesc(UUID clientHealthId,
                                                                                                    UUID relatedClientHealthId);

    @Query("""
            select rel
            from ClientRelationshipEntity rel
            where rel.tenantId = :tenantId
              and (rel.clientHealthId = :healthId or rel.relatedClientHealthId = :healthId)
            order by rel.createdAt desc
            """)
    List<ClientRelationshipEntity> findTimelineByTenantIdAndHealthId(@Param("tenantId") UUID tenantId,
                                                                     @Param("healthId") UUID healthId);
}
