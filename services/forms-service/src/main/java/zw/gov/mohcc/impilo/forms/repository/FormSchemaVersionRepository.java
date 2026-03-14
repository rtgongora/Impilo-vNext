package zw.gov.mohcc.impilo.forms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.forms.domain.FormSchemaVersionEntity;

import java.util.Optional;

@Repository
public interface FormSchemaVersionRepository extends JpaRepository<FormSchemaVersionEntity, String> {

    Optional<FormSchemaVersionEntity> findByFormSchemaIdAndVersion(String formSchemaId, int version);

    Optional<FormSchemaVersionEntity> findTopByFormSchemaIdOrderByVersionDesc(String formSchemaId);
}
