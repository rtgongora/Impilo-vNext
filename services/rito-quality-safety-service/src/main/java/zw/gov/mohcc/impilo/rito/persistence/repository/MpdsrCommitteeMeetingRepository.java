package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.rito.persistence.entity.MpdsrCommitteeMeetingEntity;

import java.util.List;
import java.util.UUID;

public interface MpdsrCommitteeMeetingRepository extends JpaRepository<MpdsrCommitteeMeetingEntity, UUID> {

    List<MpdsrCommitteeMeetingEntity> findByReviewIdOrderByMeetingAtDesc(UUID reviewId);
}
