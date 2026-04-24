package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.SiteInspectionFindingEntity;

import java.util.List;
import java.util.UUID;

public interface SiteInspectionFindingRepository extends JpaRepository<SiteInspectionFindingEntity, UUID> {
    List<SiteInspectionFindingEntity> findByInspectionId(UUID inspectionId);
}

