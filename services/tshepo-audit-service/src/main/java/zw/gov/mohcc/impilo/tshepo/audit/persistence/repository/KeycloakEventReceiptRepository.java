package zw.gov.mohcc.impilo.tshepo.audit.persistence.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.KeycloakEventReceiptEntity;
public interface KeycloakEventReceiptRepository extends JpaRepository<KeycloakEventReceiptEntity,String>{}
