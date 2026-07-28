package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.PostnatalContactEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostnatalContactRepository extends JpaRepository<PostnatalContactEntity, UUID> {

    @Query("SELECT p FROM PostnatalContactEntity p "
            + "WHERE p.tenantId = :tenantId AND p.motherCpid = :motherCpid "
            + "ORDER BY p.contactedAt DESC")
    List<PostnatalContactEntity> findByMother(@Param("tenantId") UUID tenantId,
                                              @Param("motherCpid") String motherCpid);

    @Query("SELECT p FROM PostnatalContactEntity p "
            + "WHERE p.tenantId = :tenantId AND p.pregnancyEpisodeId = :episodeId "
            + "ORDER BY p.contactedAt")
    List<PostnatalContactEntity> findByEpisode(@Param("tenantId") UUID tenantId,
                                               @Param("episodeId") UUID episodeId);

    Optional<PostnatalContactEntity> findByTenantIdAndClientOfflineId(UUID tenantId, String clientOfflineId);
}
