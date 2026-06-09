package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardRoundEntryEntity;

import java.util.List;
import java.util.UUID;

public interface WardRoundEntryRepository extends JpaRepository<WardRoundEntryEntity, UUID> {

    List<WardRoundEntryEntity> findByRoundIdOrderByReviewedAtAsc(UUID roundId);
}
