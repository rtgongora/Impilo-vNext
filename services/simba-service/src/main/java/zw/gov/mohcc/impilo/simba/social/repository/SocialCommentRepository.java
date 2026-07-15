package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SocialCommentRepository extends JpaRepository<SocialCommentEntity, Long> {

    List<SocialCommentEntity> findByPostIdAndModerationStatusOrderByIdAsc(UUID postId, String moderationStatus);

    long countByPostIdAndModerationStatus(UUID postId, String moderationStatus);
}
