package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msika.persistence.entity.RequirementSourcingMapEntity;

import java.util.Collection;
import java.util.List;

@Repository
public interface RequirementSourcingMapRepository extends JpaRepository<RequirementSourcingMapEntity, String> {

    List<RequirementSourcingMapEntity> findByRequirementCodeInAndStatus(Collection<String> requirementCodes, String status);
}
