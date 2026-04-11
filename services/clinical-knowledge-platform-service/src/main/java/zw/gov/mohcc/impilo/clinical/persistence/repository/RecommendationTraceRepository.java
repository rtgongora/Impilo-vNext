package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RecommendationTraceEntity;

import java.util.UUID;

public interface RecommendationTraceRepository extends JpaRepository<RecommendationTraceEntity, UUID> {
}
