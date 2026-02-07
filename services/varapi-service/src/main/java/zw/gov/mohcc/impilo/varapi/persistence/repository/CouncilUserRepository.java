package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilUserEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouncilUserRepository extends JpaRepository<CouncilUserEntity, Long> {

    List<CouncilUserEntity> findByCouncilId(Long councilId);

    Optional<CouncilUserEntity> findByCouncilIdAndActorId(Long councilId, String actorId);
}
