package zw.gov.mohcc.impilo.tshepo.audit.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.audit.core.AuditChainService;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.KeycloakEventReceiptEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.KeycloakEventReceiptRepository;

/** Atomically claims and appends a Keycloak event to the tamper-evident chain. */
@Service
public class KeycloakEventRecorder {
    private final AuditChainService chain;
    private final KeycloakEventReceiptRepository receipts;

    public KeycloakEventRecorder(AuditChainService chain, KeycloakEventReceiptRepository receipts) {
        this.chain = chain;
        this.receipts = receipts;
    }

    @Transactional
    public boolean record(UUID tenantId, String source, JsonNode event) {
        String key = KeycloakEventMapper.eventKey(source, event);
        if (receipts.existsById(key)) {
            return false;
        }

        KeycloakEventReceiptEntity receipt = new KeycloakEventReceiptEntity();
        receipt.setEventKey(key);
        receipt.setSource(source);
        long millis = event.path("time").asLong(0);
        receipt.setKeycloakEventTime(millis > 0 ? Instant.ofEpochMilli(millis) : null);
        receipt.setIngestedAt(Instant.now());

        // Flush the unique claim before touching the audit chain. A concurrent
        // poller fails here and the enclosing transaction leaves no partial event.
        receipts.saveAndFlush(receipt);
        chain.appendEvent(KeycloakEventMapper.map(tenantId, source, event));
        return true;
    }
}
