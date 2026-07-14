package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.config.ZiboProperties;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.AssignmentRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationJobRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ValidationLogRepository;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * $validate-code proof for the Wave 3 surgical-procedure CodeSystem (zibo V004). Uses the SAME concept
 * content the migration seeds so a valid procedure code passes and an unknown code is flagged — the
 * ValidationEngine.validateCoding path (POST /v1/validate/coding) exercised end-to-end over the seed.
 */
@ExtendWith(MockitoExtension.class)
class SurgicalProcedureCodeValidationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CORRELATION = UUID.randomUUID();
    private static final String SYSTEM = "https://impilo.gov.zw/fhir/CodeSystem/surgical-procedures";

    // Mirrors the concept[] seeded by V004__surgical_procedure_codes.sql.
    private static final String CONTENT = """
            {"resourceType":"CodeSystem","url":"https://impilo.gov.zw/fhir/CodeSystem/surgical-procedures",
             "version":"1.0.0","status":"active","content":"complete","concept":[
              {"code":"47.0","display":"Appendicectomy"},
              {"code":"74.1","display":"Low cervical caesarean section"},
              {"code":"53.00","display":"Unilateral repair of inguinal hernia"},
              {"code":"79.36","display":"Open reduction of fracture with internal fixation (ORIF)"},
              {"code":"54.11","display":"Exploratory laparotomy"}]}""";

    @Mock private ArtifactRepository artifactRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private ValidationJobRepository validationJobRepository;
    @Mock private ValidationLogRepository validationLogRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ValidationEngine engine;

    @BeforeEach
    void setUp() {
        ZiboProperties props = new ZiboProperties();
        props.getValidation().setDefaultMode("STRICT");
        engine = new ValidationEngine(artifactRepository, assignmentRepository, validationJobRepository,
                validationLogRepository, outboxRepository, props, new ObjectMapper());
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT, "surgeon-1", "PROVIDER", "TREATMENT",
                null, CORRELATION, null, null, null, AccessMode.INTERNAL);
    }

    private ArtifactEntity publishedCodeSystem() {
        ArtifactEntity a = new ArtifactEntity();
        a.setArtifactId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setFhirType(ArtifactType.CODE_SYSTEM);
        a.setCanonicalUrl(SYSTEM);
        a.setVersion("1.0.0");
        a.setName("ImpiloSurgicalProcedures");
        a.setStatus(ArtifactStatus.PUBLISHED);
        a.setContentJson(CONTENT);
        a.setContentHash("hash-surgical");
        a.setCreatedBy("system-seeder");
        a.setPublishedBy("system-seeder");
        a.setPublishedAt(OffsetDateTime.now());
        a.setCreatedAt(OffsetDateTime.now());
        a.setUpdatedAt(OffsetDateTime.now());
        return a;
    }

    @Test
    void validateKnownSurgicalCodeIsValid() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            h.when(TrustContextHolder::get).thenReturn(ctx());
            when(artifactRepository.findByTenantIdAndCanonicalUrl(TENANT, SYSTEM))
                    .thenReturn(List.of(publishedCodeSystem()));

            ValidationEngine.ValidationResult r =
                    engine.validateCoding(SYSTEM, "54.11", "Exploratory laparotomy", PolicyMode.STRICT, null);

            assertThat(r.valid()).isTrue();
            assertThat(r.issues()).isEmpty();
        }
    }

    @Test
    void validateUnknownSurgicalCodeIsInvalidInStrictMode() {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(ctx());
            h.when(TrustContextHolder::get).thenReturn(ctx());
            when(artifactRepository.findByTenantIdAndCanonicalUrl(TENANT, SYSTEM))
                    .thenReturn(List.of(publishedCodeSystem()));

            ValidationEngine.ValidationResult r =
                    engine.validateCoding(SYSTEM, "99.99", "Nonexistent", PolicyMode.STRICT, null);

            assertThat(r.valid()).isFalse();
            assertThat(r.issues()).isNotEmpty();
        }
    }
}
