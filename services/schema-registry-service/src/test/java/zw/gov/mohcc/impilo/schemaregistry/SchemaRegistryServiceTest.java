package zw.gov.mohcc.impilo.schemaregistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.schemaregistry.api.dto.RegisterSchemaRequest;
import zw.gov.mohcc.impilo.schemaregistry.core.SchemaRegistryService;
import zw.gov.mohcc.impilo.schemaregistry.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.schemaregistry.domain.SchemaVersionEntity;
import zw.gov.mohcc.impilo.schemaregistry.domain.SubjectEntity;
import zw.gov.mohcc.impilo.schemaregistry.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.schemaregistry.repository.SchemaVersionRepository;
import zw.gov.mohcc.impilo.schemaregistry.repository.SubjectRepository;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchemaRegistryServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaa1111-1111-1111-1111-111111111111");

    private SubjectRepository subjectRepo;
    private SchemaVersionRepository versionRepo;
    private OutboxEventRepository outboxRepo;
    private SchemaRegistryService service;

    @BeforeEach
    void setUp() {
        subjectRepo = mock(SubjectRepository.class);
        versionRepo = mock(SchemaVersionRepository.class);
        outboxRepo = mock(OutboxEventRepository.class);

        when(subjectRepo.save(any(SubjectEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepo.save(any(SchemaVersionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxRepo.save(any(OutboxEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new SchemaRegistryService(subjectRepo, versionRepo, outboxRepo, new ObjectMapper());
    }

    @Nested
    @DisplayName("Schema Registration")
    class Registration {

        @Test
        @DisplayName("First schema version registers successfully")
        void firstVersionRegisters() {
            when(subjectRepo.findBySubjectName("impilo.vito.patient.created.v1")).thenReturn(Optional.empty());
            when(versionRepo.findFirstBySubjectIdOrderByVersionDesc(any())).thenReturn(Optional.empty());

            String schema = """
                    {"type":"object","properties":{"name":{"type":"string"},"age":{"type":"integer"}},"required":["name"]}
                    """;

            var request = new RegisterSchemaRequest("impilo.vito.patient.created.v1", schema, "Patient created event");
            Map<String, Object> result = service.registerSchema(TENANT_ID, "corr-001", "idem-001", request);

            assertThat(result.get("version")).isEqualTo(1);
            assertThat(result.get("compatible")).isEqualTo(true);
            assertThat(result.get("subject")).isEqualTo("impilo.vito.patient.created.v1");
            assertThat(result.get("fingerprint")).isNotNull();

            verify(subjectRepo).save(any());
            verify(versionRepo).save(argThat(v -> v.getVersion() == 1));
            verify(outboxRepo).save(argThat(e ->
                    "impilo.schema-registry.schema.registered.v1".equals(e.getEventType())));
        }

        @Test
        @DisplayName("Backward-compatible change registers as next version")
        void backwardCompatibleChange() {
            SubjectEntity subject = new SubjectEntity();
            subject.setSubjectName("impilo.vito.patient.created.v1");
            when(subjectRepo.findBySubjectName("impilo.vito.patient.created.v1")).thenReturn(Optional.of(subject));

            SchemaVersionEntity existing = new SchemaVersionEntity();
            existing.setVersion(1);
            existing.setSchemaJson("""
                    {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
                    """);
            when(versionRepo.findFirstBySubjectIdOrderByVersionDesc(any())).thenReturn(Optional.of(existing));

            // Adding optional field = backward compatible
            String newSchema = """
                    {"type":"object","properties":{"name":{"type":"string"},"email":{"type":"string"}},"required":["name"]}
                    """;

            var request = new RegisterSchemaRequest("impilo.vito.patient.created.v1", newSchema, null);
            Map<String, Object> result = service.registerSchema(TENANT_ID, "corr-002", "idem-002", request);

            assertThat(result.get("version")).isEqualTo(2);
            assertThat(result.get("compatible")).isEqualTo(true);
        }

        @Test
        @DisplayName("Breaking change is rejected")
        void breakingChangeRejected() {
            SubjectEntity subject = new SubjectEntity();
            subject.setSubjectName("impilo.vito.patient.created.v1");
            when(subjectRepo.findBySubjectName("impilo.vito.patient.created.v1")).thenReturn(Optional.of(subject));

            SchemaVersionEntity existing = new SchemaVersionEntity();
            existing.setVersion(1);
            existing.setSchemaJson("""
                    {"type":"object","properties":{"name":{"type":"string"},"age":{"type":"integer"}},"required":["name"]}
                    """);
            when(versionRepo.findFirstBySubjectIdOrderByVersionDesc(any())).thenReturn(Optional.of(existing));

            // Removing a field = breaking
            String breakingSchema = """
                    {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
                    """;

            var request = new RegisterSchemaRequest("impilo.vito.patient.created.v1", breakingSchema, null);
            Map<String, Object> result = service.registerSchema(TENANT_ID, "corr-003", "idem-003", request);

            assertThat(result.get("compatible")).isEqualTo(false);
            assertThat(result).containsKey("violations");

            // Should NOT have saved a new version
            verify(versionRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Compatibility Check")
    class CompatibilityCheck {

        @Test
        @DisplayName("New subject is always compatible")
        void newSubjectIsCompatible() {
            when(subjectRepo.findBySubjectName("new-subject")).thenReturn(Optional.empty());

            var result = service.checkCompatibility("new-subject", "{}");

            assertThat(result.compatible()).isTrue();
        }
    }
}
