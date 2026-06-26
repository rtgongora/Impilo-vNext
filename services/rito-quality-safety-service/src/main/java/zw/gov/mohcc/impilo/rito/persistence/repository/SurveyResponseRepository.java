package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.SurveyResponseEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SurveyResponseRepository extends JpaRepository<SurveyResponseEntity, UUID> {

    List<SurveyResponseEntity> findBySurveyId(UUID surveyId);

    List<SurveyResponseEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);

    long countBySurveyId(UUID surveyId);
}
