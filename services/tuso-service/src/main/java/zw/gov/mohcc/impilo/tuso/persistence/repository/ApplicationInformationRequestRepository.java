package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ApplicationInformationRequestEntity;

import java.util.List;
import java.util.UUID;

public interface ApplicationInformationRequestRepository extends JpaRepository<ApplicationInformationRequestEntity, UUID> {
    List<ApplicationInformationRequestEntity> findByApplicationIdOrderByRequestedAtDesc(UUID applicationId);
    long countByApplicationIdAndStatus(UUID applicationId, String status);
}
