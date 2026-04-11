package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.clinical.persistence.entity.PathwayDefinitionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PathwayDefinitionRepository extends JpaRepository<PathwayDefinitionEntity, UUID> {

    @Query("""
            select distinct p from PathwayDefinitionEntity p
            left join fetch p.steps
            where p.status = :status
            """)
    List<PathwayDefinitionEntity> findAllActiveWithSteps(@Param("status") String status);

    @Query("""
            select distinct p from PathwayDefinitionEntity p
            left join fetch p.steps
            where p.id = :id
            """)
    Optional<PathwayDefinitionEntity> findByIdWithSteps(@Param("id") UUID id);
}
