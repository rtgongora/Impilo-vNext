package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormSignatureEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FormSignatureRepository extends JpaRepository<FormSignatureEntity, UUID> {

    List<FormSignatureEntity> findByResponseId(UUID responseId);

    boolean existsByResponseIdAndSignatureKind(UUID responseId, String signatureKind);
}
