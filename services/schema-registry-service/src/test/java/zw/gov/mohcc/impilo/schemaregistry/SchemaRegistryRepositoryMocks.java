package zw.gov.mohcc.impilo.schemaregistry;

import zw.gov.mohcc.impilo.schemaregistry.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.schemaregistry.domain.SchemaVersionEntity;
import zw.gov.mohcc.impilo.schemaregistry.domain.SubjectEntity;
import zw.gov.mohcc.impilo.schemaregistry.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.schemaregistry.repository.SchemaVersionRepository;
import zw.gov.mohcc.impilo.schemaregistry.repository.SubjectRepository;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Mockito stubs that mimic JPA {@code @GeneratedValue} for unit tests. */
public final class SchemaRegistryRepositoryMocks {

    private SchemaRegistryRepositoryMocks() {}

    public static void stubPersistence(
            SubjectRepository subjectRepo,
            SchemaVersionRepository versionRepo,
            OutboxEventRepository outboxRepo) {

        when(subjectRepo.save(any(SubjectEntity.class))).thenAnswer(inv -> {
            SubjectEntity s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        when(versionRepo.save(any(SchemaVersionEntity.class))).thenAnswer(inv -> {
            SchemaVersionEntity v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(UUID.randomUUID());
            }
            return v;
        });
        when(outboxRepo.save(any(OutboxEventEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }
}
