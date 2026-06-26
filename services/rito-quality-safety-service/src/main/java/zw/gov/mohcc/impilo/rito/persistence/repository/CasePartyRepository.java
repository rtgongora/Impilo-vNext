package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.CasePartyEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CasePartyRepository extends JpaRepository<CasePartyEntity, UUID> {

    List<CasePartyEntity> findByCaseId(UUID caseId);
}
