package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vito.persistence.entity.PatientShareArtifactEntity;

import java.util.List;
import java.util.Optional;

public interface PatientShareArtifactRepository extends JpaRepository<PatientShareArtifactEntity, Long> {

    Optional<PatientShareArtifactEntity> findByTokenHash(String tokenHash);

    List<PatientShareArtifactEntity> findByShareRequestId(long shareRequestId);
}
