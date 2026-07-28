package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRelationshipTypeEntity;

import java.util.List;

public interface FacilityRelationshipTypeRepository
        extends JpaRepository<FacilityRelationshipTypeEntity, String> {

    List<FacilityRelationshipTypeEntity> findByActiveTrueOrderBySortOrderAsc();
}
