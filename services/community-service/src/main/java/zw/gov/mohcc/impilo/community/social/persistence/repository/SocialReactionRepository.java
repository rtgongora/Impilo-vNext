package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialReactionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialReactionRepository extends JpaRepository<SocialReactionEntity, Long> {
    Optional<SocialReactionEntity> findFirstByTargetTypeAndTargetIdAndActorId(String targetType, UUID targetId, String actorId);
    List<SocialReactionEntity> findByTargetTypeAndTargetId(String targetType, UUID targetId);
    long countByTargetTypeAndTargetId(String targetType, UUID targetId);
}
