package zw.gov.mohcc.impilo.shareslip.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareLinkEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLinkEntity, Long> {

    Optional<ShareLinkEntity> findByShareToken(String shareToken);

    Optional<ShareLinkEntity> findByLinkId(UUID linkId);

    Page<ShareLinkEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<ShareLinkEntity> findBySubjectTypeAndSubjectId(String subjectType, String subjectId, Pageable pageable);

    @Query("SELECT s FROM ShareLinkEntity s WHERE s.status = 'ACTIVE' AND s.expiresAt < :now")
    List<ShareLinkEntity> findExpiredActive(@Param("now") OffsetDateTime now);
}
