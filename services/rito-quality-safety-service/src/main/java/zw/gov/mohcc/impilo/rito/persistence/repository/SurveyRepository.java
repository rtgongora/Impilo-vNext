package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.SurveyEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SurveyRepository extends JpaRepository<SurveyEntity, UUID> {

    Optional<SurveyEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<SurveyEntity> findByTenantIdAndSurveyKey(UUID tenantId, String surveyKey);

    List<SurveyEntity> findByTenantIdAndActiveTrue(UUID tenantId);

    boolean existsByTenantIdAndSurveyKey(UUID tenantId, String surveyKey);
}
