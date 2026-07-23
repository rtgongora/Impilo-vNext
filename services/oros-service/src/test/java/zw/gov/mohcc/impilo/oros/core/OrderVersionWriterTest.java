package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderItemEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderVersionRepository;
import zw.gov.mohcc.impilo.oros.testsupport.OrosTestObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OrderVersionWriter}: the canonical serialisation must be
 * deterministic (stable key and item ordering) so the SHA-256 content hash — the
 * OF-B2 signing input — is reproducible for identical content and differs on change.
 */
@ExtendWith(MockitoExtension.class)
class OrderVersionWriterTest {

    @Mock private OrderVersionRepository versionRepository;
    @Mock private OrderItemRepository orderItemRepository;

    private OrderVersionWriter writer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = OrosTestObjectMapper.create();
        writer = new OrderVersionWriter(versionRepository, orderItemRepository, objectMapper);
    }

    private OrderEntity order() {
        OrderEntity order = new OrderEntity();
        order.setOrderId("01ARZ3NDEKTSV4RRFFQ69G5FAV");
        order.setTenantId(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        order.setFacilityId(UUID.fromString("00000000-0000-4000-8000-000000000002"));
        order.setPatientCpid("CPID-1");
        order.setOrderType(OrderType.PHARMACY);
        order.setPriority(OrderPriority.ROUTINE);
        order.setClinicalNotes("Take with food");
        return order;
    }

    private static OrderItemEntity item(String code, String name) {
        OrderItemEntity i = new OrderItemEntity();
        i.setCode(code);
        i.setDisplayName(name);
        i.setQuantity(1);
        return i;
    }

    @Test
    void canonicalContentIsDeterministicRegardlessOfItemOrder() {
        OrderEntity order = order();
        String a = writer.canonicalContent(order, List.of(item("B02", "Paracetamol"), item("A01", "Amoxicillin")));
        String b = writer.canonicalContent(order, List.of(item("A01", "Amoxicillin"), item("B02", "Paracetamol")));
        assertThat(a).isEqualTo(b);
        assertThat(OrderVersionWriter.sha256Hex(a)).isEqualTo(OrderVersionWriter.sha256Hex(b));
    }

    @Test
    void contentHashChangesWhenContentChanges() {
        OrderEntity order = order();
        String before = writer.canonicalContent(order, List.of(item("A01", "Amoxicillin")));
        order.setClinicalNotes("Take after food");
        String after = writer.canonicalContent(order, List.of(item("A01", "Amoxicillin")));
        assertThat(OrderVersionWriter.sha256Hex(before)).isNotEqualTo(OrderVersionWriter.sha256Hex(after));
    }

    @Test
    void canonicalContentExcludesFulfilmentState() {
        // A version captures WHAT was ordered — status/workflow progress must not alter the hash.
        OrderEntity order = order();
        String placed = writer.canonicalContent(order, List.of());
        order.setStatus(zw.gov.mohcc.impilo.oros.domain.OrderStatus.IN_PROGRESS);
        order.setWorkflowState("SPECIMEN_COLLECTED");
        String inProgress = writer.canonicalContent(order, List.of());
        assertThat(placed).isEqualTo(inProgress);
    }

    @Test
    void sha256IsLowercaseHex64() {
        String hash = OrderVersionWriter.sha256Hex("payload");
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }
}
