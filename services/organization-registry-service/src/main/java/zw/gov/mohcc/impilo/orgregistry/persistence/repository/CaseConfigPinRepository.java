package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.CaseConfigPinEntity;

import java.util.List;
import java.util.UUID;

public interface CaseConfigPinRepository extends JpaRepository<CaseConfigPinEntity, UUID> {

    List<CaseConfigPinEntity> findByBindingId(UUID bindingId);
}
