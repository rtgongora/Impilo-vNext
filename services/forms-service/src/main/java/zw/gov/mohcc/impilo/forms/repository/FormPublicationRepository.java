package zw.gov.mohcc.impilo.forms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.forms.domain.FormPublicationEntity;

import java.util.List;

@Repository
public interface FormPublicationRepository extends JpaRepository<FormPublicationEntity, String> {

    List<FormPublicationEntity> findByFormIdOrderByPublishedAtDesc(String formId);
}
