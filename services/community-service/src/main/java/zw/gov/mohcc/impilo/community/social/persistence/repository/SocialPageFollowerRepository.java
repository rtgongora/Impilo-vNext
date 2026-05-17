package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialPageFollowerEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialPageFollowerRepository extends JpaRepository<SocialPageFollowerEntity, Long> {
    Optional<SocialPageFollowerEntity> findFirstByPageIdAndActorId(UUID pageId, String actorId);
    List<SocialPageFollowerEntity> findByActorId(String actorId);
}
