package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.MessageReceiptEntity;

import java.util.List;
import java.util.UUID;

public interface MessageReceiptRepository extends JpaRepository<MessageReceiptEntity, UUID> {

    boolean existsByMessageIdAndActorIdAndReceiptState(UUID messageId, String actorId, String receiptState);

    List<MessageReceiptEntity> findByMessageId(UUID messageId);
}
