package zw.gov.mohcc.impilo.procedures.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procedures.persistence.entity.AftercareTemplateChannelEntity;

import java.util.List;
import java.util.UUID;

public interface AftercareTemplateChannelRepository
        extends JpaRepository<AftercareTemplateChannelEntity, AftercareTemplateChannelEntity.Key> {
    List<AftercareTemplateChannelEntity> findByTemplateId(UUID templateId);
}
