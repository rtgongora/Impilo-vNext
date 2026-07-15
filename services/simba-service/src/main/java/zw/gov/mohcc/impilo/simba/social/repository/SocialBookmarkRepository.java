package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialBookmarkEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialBookmarkRepository extends JpaRepository<SocialBookmarkEntity, Long> {

    Optional<SocialBookmarkEntity> findByActorCpidAndSubjectTypeAndSubjectId(
            String actorCpid, String subjectType, UUID subjectId);

    List<SocialBookmarkEntity> findByTenantIdAndActorCpidOrderByIdDesc(UUID tenantId, String actorCpid);
}
