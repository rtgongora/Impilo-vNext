package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.rito.persistence.entity.MpdsrFindingEntity;

import java.util.List;
import java.util.UUID;

public interface MpdsrFindingRepository extends JpaRepository<MpdsrFindingEntity, UUID> {

    List<MpdsrFindingEntity> findByReviewIdOrderByCreatedAtAsc(UUID reviewId);
}
