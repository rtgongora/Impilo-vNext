package zw.gov.mohcc.impilo.vito.persistence.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Identity Contract §1 / C5 — CRID coherence invariant.
 *
 * <p>HID (health_id) is the identity anchor; CRID is the demographic-record
 * lineage/merge anchor. They are independent identifiers: for every NEW client
 * both are minted separately and must never be equal. (Rows created before the
 * V011 backfill share the value historically — that equivalence is documented
 * legacy, not a rule.)</p>
 */
@DisplayName("ClientEntity identifier invariants")
class ClientEntityIdentifierInvariantTest {

    private ClientEntity freshlyPersistedClient() throws Exception {
        ClientEntity client = new ClientEntity();
        // invoke the @PrePersist hook the way JPA would
        Method prePersist = null;
        for (Method m : ClientEntity.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(jakarta.persistence.PrePersist.class)) {
                prePersist = m;
                break;
            }
        }
        assertNotNull(prePersist, "ClientEntity must have a @PrePersist hook minting identifiers");
        prePersist.setAccessible(true);
        prePersist.invoke(client);
        return client;
    }

    @Test
    @DisplayName("new clients mint independent HID and CRID — never equal")
    void newClient_hidAndCridIndependent() throws Exception {
        for (int i = 0; i < 100; i++) {
            ClientEntity client = freshlyPersistedClient();
            assertNotNull(client.getHealthId(), "HID must be minted at persist");
            assertNotNull(client.getCrid(), "CRID must be minted at persist");
            assertNotEquals(client.getHealthId(), client.getCrid(),
                    "HID and CRID are independent anchors (Identity Contract §1); "
                    + "equal values would recreate the pre-V011 dual-anchor ambiguity");
        }
    }

    @Test
    @DisplayName("identifiers are random across instances")
    void identifiersAreRandomAcrossInstances() throws Exception {
        ClientEntity a = freshlyPersistedClient();
        ClientEntity b = freshlyPersistedClient();
        assertNotEquals(a.getHealthId(), b.getHealthId());
        assertNotEquals(a.getCrid(), b.getCrid());
    }
}
