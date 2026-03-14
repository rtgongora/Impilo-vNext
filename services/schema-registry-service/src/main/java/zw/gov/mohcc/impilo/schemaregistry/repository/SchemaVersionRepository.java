package zw.gov.mohcc.impilo.schemaregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.schemaregistry.domain.SchemaVersionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchemaVersionRepository extends JpaRepository<SchemaVersionEntity, UUID> {
    List<SchemaVersionEntity> findBySubjectIdOrderByVersionDesc(UUID subjectId);
    Optional<SchemaVersionEntity> findBySubjectIdAndVersion(UUID subjectId, int version);
    Optional<SchemaVersionEntity> findFirstBySubjectIdOrderByVersionDesc(UUID subjectId);
}
