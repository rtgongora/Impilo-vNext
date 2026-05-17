package zw.gov.mohcc.impilo.community.social.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.social.persistence.entity.SocialCommentEntity;

import java.util.List;
import java.util.UUID;

public interface SocialCommentRepository extends JpaRepository<SocialCommentEntity, UUID> {
    List<SocialCommentEntity> findByPostIdOrderByCreatedAtAsc(UUID postId);
    long countByPostId(UUID postId);
}
