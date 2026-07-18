package zw.gov.mohcc.impilo.tshepo.identity.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.tshepo.identity.core.IdResolutionService;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.IdMappingEntity;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SubjectTranslationRelay} + {@link SubjectEventPublisher}.
 *
 * <p>The load-bearing invariant (Identity Contract §7.3): everything emitted on
 * {@code impilo.identity.subject} carries CPIDs only — no {@code health_id},
 * {@code healthId}, CRID, DID, or demographic field may survive translation,
 * even when the incoming VITO payload smuggles extra fields.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubjectTranslationRelay")
class SubjectTranslationRelayTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HEALTH_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MERGED_HEALTH_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CPID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID MERGED_CPID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

    /** Fields that must never appear anywhere in a subject event. */
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "health_id", "healthId", "survivorHealthId", "mergedHealthId",
            "survivor_health_id", "merged_health_id",
            "crid", "did", "givenName", "familyName", "name", "nationalId",
            "national_id", "phone", "address", "dateOfBirth", "date_of_birth");

    @Mock
    private IdResolutionService idResolutionService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SubjectTranslationRelay relay;

    @BeforeEach
    void setUp() {
        relay = new SubjectTranslationRelay(
                objectMapper, idResolutionService, new SubjectEventPublisher(kafkaTemplate));
    }

    private IdMappingEntity mapping(UUID healthId, UUID cpid) {
        IdMappingEntity m = new IdMappingEntity();
        m.setId(1L);
        m.setTenantId(TENANT);
        m.setHealthId(healthId);
        m.setCpid(cpid);
        m.setMappingStatus("ACTIVE");
        return m;
    }

    private String capturedMessage() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(SubjectEventPublisher.SUBJECT_TOPIC), any(), captor.capture());
        return captor.getValue();
    }

    private void assertNoForbiddenFields(String message) throws Exception {
        JsonNode root = objectMapper.readTree(message);
        Set<String> found = new LinkedHashSet<>();
        collectFieldNames(root, found);
        for (String forbidden : FORBIDDEN_FIELDS) {
            assertFalse(found.contains(forbidden),
                    "Subject event must not contain field '" + forbidden + "': " + message);
        }
        assertFalse(message.contains(HEALTH_ID.toString()),
                "Subject event must not contain the Health ID value anywhere: " + message);
        assertFalse(message.contains(MERGED_HEALTH_ID.toString()),
                "Subject event must not contain the merged Health ID value anywhere: " + message);
    }

    private static void collectFieldNames(JsonNode node, Set<String> out) {
        if (node == null) {
            return;
        }
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String field = it.next();
            out.add(field);
            collectFieldNames(node.get(field), out);
        }
        if (node.isArray()) {
            node.forEach(child -> collectFieldNames(child, out));
        }
    }

    // ── Identity lifecycle translation ──────────────────────────────────────

    @Nested
    @DisplayName("identity events")
    class IdentityEvents {

        @Test
        @DisplayName("IDENTITY_CREATED (legacy raw payload) → subject.created with CPID only")
        void created_legacyRaw_translatesToCpid() throws Exception {
            when(idResolutionService.findOrCreateMapping(TENANT, HEALTH_ID, null))
                    .thenReturn(mapping(HEALTH_ID, CPID));

            String vitoEvent = String.format(
                    "{\"healthId\":\"%s\",\"status\":\"PROVISIONAL\",\"did\":\"did:impilo:abc\",\"tenantId\":\"%s\"}",
                    HEALTH_ID, TENANT);
            relay.onVitoIdentity(vitoEvent);

            String message = capturedMessage();
            JsonNode root = objectMapper.readTree(message);
            assertEquals("impilo.identity.subject.created.v1", root.get("event_type").asText());
            assertEquals(CPID.toString(), root.get("payload").get("cpid").asText());
            assertEquals("PROVISIONAL", root.get("payload").get("status").asText());
            assertNoForbiddenFields(message);
        }

        @Test
        @DisplayName("PII-smuggling payload: extra fields are stripped, never forwarded")
        void created_smuggledPii_isStripped() throws Exception {
            when(idResolutionService.findOrCreateMapping(TENANT, HEALTH_ID, null))
                    .thenReturn(mapping(HEALTH_ID, CPID));

            String vitoEvent = String.format(
                    "{\"event_type\":\"impilo.vito.client.created.v1\",\"tenant_id\":\"%s\","
                    + "\"payload\":{\"healthId\":\"%s\",\"status\":\"PROVISIONAL\","
                    + "\"givenName\":\"Thandiwe\",\"familyName\":\"Moyo\",\"nationalId\":\"63-123456A70\","
                    + "\"phone\":\"+263771234567\",\"crid\":\"11111111-1111-1111-1111-111111111111\"}}",
                    TENANT, HEALTH_ID);
            relay.onVitoIdentity(vitoEvent);

            assertNoForbiddenFields(capturedMessage());
        }

        @Test
        @DisplayName("v1.1 envelope IDENTITY_VERIFIED → subject.verified")
        void verified_envelope_translates() throws Exception {
            when(idResolutionService.findOrCreateMapping(TENANT, HEALTH_ID, null))
                    .thenReturn(mapping(HEALTH_ID, CPID));

            String vitoEvent = String.format(
                    "{\"event_type\":\"impilo.vito.client.verified.v1\",\"correlation_id\":\"corr-1\","
                    + "\"payload\":{\"healthId\":\"%s\",\"verifiedBy\":\"steward-1\",\"tenantId\":\"%s\"}}",
                    HEALTH_ID, TENANT);
            relay.onVitoIdentity(vitoEvent);

            String message = capturedMessage();
            JsonNode root = objectMapper.readTree(message);
            assertEquals("impilo.identity.subject.verified.v1", root.get("event_type").asText());
            assertEquals("corr-1", root.get("correlation_id").asText());
            assertNoForbiddenFields(message);
        }

        @Test
        @DisplayName("IDENTITY_DECEASED for an unmapped identity emits nothing")
        void deceased_unmapped_skips() {
            when(idResolutionService.findExistingMapping(TENANT, HEALTH_ID))
                    .thenReturn(Optional.empty());

            String vitoEvent = String.format(
                    "{\"event_type\":\"impilo.vito.client.deceased.v1\","
                    + "\"payload\":{\"healthId\":\"%s\",\"tenantId\":\"%s\"}}",
                    HEALTH_ID, TENANT);
            relay.onVitoIdentity(vitoEvent);

            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("double-encoded (JSON string literal) wire value is unwrapped")
        void created_doubleEncoded_isUnwrapped() throws Exception {
            when(idResolutionService.findOrCreateMapping(TENANT, HEALTH_ID, null))
                    .thenReturn(mapping(HEALTH_ID, CPID));

            String raw = String.format(
                    "{\"healthId\":\"%s\",\"status\":\"PROVISIONAL\",\"did\":\"did:impilo:abc\",\"tenantId\":\"%s\"}",
                    HEALTH_ID, TENANT);
            String doubleEncoded = objectMapper.writeValueAsString(raw);
            relay.onVitoIdentity(doubleEncoded);

            assertNoForbiddenFields(capturedMessage());
        }

        @Test
        @DisplayName("unparseable message is dropped without emitting")
        void garbage_isDropped() {
            relay.onVitoIdentity("not-json{{{");
            verifyNoInteractions(kafkaTemplate, idResolutionService);
        }
    }

    // ── Merge translation + mapping lifecycle ───────────────────────────────

    @Nested
    @DisplayName("merge events")
    class MergeEvents {

        private String mergeEvent(String type) {
            return String.format(
                    "{\"event_type\":\"%s\",\"payload\":{\"tenantId\":\"%s\","
                    + "\"survivor\":\"crid-1\",\"merged\":\"crid-2\","
                    + "\"survivorHealthId\":\"%s\",\"mergedHealthId\":\"%s\"}}",
                    type, TENANT, HEALTH_ID, MERGED_HEALTH_ID);
        }

        @Test
        @DisplayName("merge → retires merged mapping and emits survivor/merged CPIDs only")
        void merge_retiresAndTranslates() throws Exception {
            when(idResolutionService.findOrCreateMapping(TENANT, HEALTH_ID, null))
                    .thenReturn(mapping(HEALTH_ID, CPID));
            when(idResolutionService.findExistingMapping(TENANT, MERGED_HEALTH_ID))
                    .thenReturn(Optional.of(mapping(MERGED_HEALTH_ID, MERGED_CPID)));

            relay.onVitoDedup(mergeEvent("vito.merge.executed"));

            verify(idResolutionService).retireMappingForMerge(TENANT, MERGED_HEALTH_ID, CPID);
            String message = capturedMessage();
            JsonNode root = objectMapper.readTree(message);
            assertEquals("impilo.identity.subject.merged.v1", root.get("event_type").asText());
            assertEquals(CPID.toString(), root.get("payload").get("survivor_cpid").asText());
            assertEquals(MERGED_CPID.toString(), root.get("payload").get("merged_cpid").asText());
            assertNoForbiddenFields(message);
        }

        @Test
        @DisplayName("merge reversal → reactivates mapping and emits merge_reversed")
        void mergeReversed_reactivates() throws Exception {
            when(idResolutionService.findOrCreateMapping(TENANT, HEALTH_ID, null))
                    .thenReturn(mapping(HEALTH_ID, CPID));
            when(idResolutionService.findExistingMapping(TENANT, MERGED_HEALTH_ID))
                    .thenReturn(Optional.of(mapping(MERGED_HEALTH_ID, MERGED_CPID)));

            relay.onVitoDedup(mergeEvent("vito.merge.reversed"));

            verify(idResolutionService).reactivateMappingAfterUnmerge(TENANT, MERGED_HEALTH_ID);
            String message = capturedMessage();
            assertEquals("impilo.identity.subject.merge_reversed.v1",
                    objectMapper.readTree(message).get("event_type").asText());
            assertNoForbiddenFields(message);
        }

        @Test
        @DisplayName("merge of an unmapped identity emits nothing (nothing to repoint)")
        void merge_unmappedMerged_skips() {
            when(idResolutionService.findOrCreateMapping(TENANT, HEALTH_ID, null))
                    .thenReturn(mapping(HEALTH_ID, CPID));
            when(idResolutionService.findExistingMapping(TENANT, MERGED_HEALTH_ID))
                    .thenReturn(Optional.empty());

            relay.onVitoDedup(mergeEvent("vito.merge.executed"));

            verify(idResolutionService, never()).retireMappingForMerge(any(), any(), any());
            verifyNoInteractions(kafkaTemplate);
        }
    }
}
