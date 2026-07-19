package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.indawo.domain.PlaceLinkEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaceLinkRepository extends JpaRepository<PlaceLinkEntity, UUID> {

    Optional<PlaceLinkEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Both-direction read: every link where the ref appears on either side. */
    @Query("SELECT l FROM PlaceLinkEntity l WHERE l.tenantId = :tenantId AND l.status = :status"
            + " AND ((l.leftRefType = :refType AND l.leftRefId = :refId)"
            + "   OR (l.rightRefType = :refType AND l.rightRefId = :refId))"
            + " ORDER BY l.createdAt")
    List<PlaceLinkEntity> findByRef(@Param("tenantId") UUID tenantId,
                                    @Param("refType") String refType,
                                    @Param("refId") UUID refId,
                                    @Param("status") String status);

    Optional<PlaceLinkEntity> findFirstByTenantIdAndLeftRefTypeAndLeftRefIdAndRightRefTypeAndRightRefIdAndLinkTypeAndStatus(
            UUID tenantId, String leftRefType, UUID leftRefId,
            String rightRefType, UUID rightRefId, String linkType, String status);
}
