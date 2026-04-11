package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.clinical.persistence.entity.KnowledgeReviewItemEntity;

import java.util.List;
import java.util.UUID;

public interface KnowledgeReviewItemRepository extends JpaRepository<KnowledgeReviewItemEntity, UUID> {

    List<KnowledgeReviewItemEntity> findByReviewStatusOrderByCreatedAtAsc(String reviewStatus);
}
