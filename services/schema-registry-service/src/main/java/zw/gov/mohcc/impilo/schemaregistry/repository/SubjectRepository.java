package zw.gov.mohcc.impilo.schemaregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.schemaregistry.domain.SubjectEntity;

import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<SubjectEntity, UUID> {
    Optional<SubjectEntity> findBySubjectName(String subjectName);
}
