package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialBookmarkEntity;

import java.util.Optional;
import java.util.UUID;

public interface SocialBookmarkRepository extends JpaRepository<SocialBookmarkEntity, Long> {
    Optional<SocialBookmarkEntity> findFirstByActorIdAndPostId(String actorId, UUID postId);
    long countByPostId(UUID postId);
}
