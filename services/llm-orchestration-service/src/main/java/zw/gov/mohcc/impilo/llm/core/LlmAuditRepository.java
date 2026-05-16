package zw.gov.mohcc.impilo.llm.core;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmAuditRepository extends JpaRepository<LlmAuditEntity, Long> {
}
