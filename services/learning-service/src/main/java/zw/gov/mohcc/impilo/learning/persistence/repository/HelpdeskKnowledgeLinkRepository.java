package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.learning.persistence.entity.HelpdeskKnowledgeLinkEntity;

public interface HelpdeskKnowledgeLinkRepository extends JpaRepository<HelpdeskKnowledgeLinkEntity, UUID> {

    @Query(
            """
            select h from HelpdeskKnowledgeLinkEntity h
            where h.tenantId = :tenantId and h.active = true and h.issueTypeOrTopic = :issueType
            """)
    List<HelpdeskKnowledgeLinkEntity> findForIssueType(
            @Param("tenantId") UUID tenantId, @Param("issueType") String issueTypeOrTopic);
}
