package zw.gov.mohcc.impilo.learning.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningResourceEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningCompletionRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningProgressRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningResourceRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.MoodleWsSnapshotRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearningMoodleIngestFacadeTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private LearningPlatformFacade facade;

    @Autowired
    private LearningResourceRepository resourceRepository;

    @Autowired
    private LearningCompletionRepository completionRepository;

    @Autowired
    private LearningProgressRepository progressRepository;

    @Autowired
    private MoodleWsSnapshotRepository snapshotRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void seedResource() {
        completionRepository.deleteAll();
        progressRepository.deleteAll();
        snapshotRepository.deleteAll();
        resourceRepository.deleteAll();
        LearningResourceEntity r = new LearningResourceEntity();
        r.setTenantId(TENANT);
        r.setResourceCode("RES_MOODLE_TEST");
        r.setSourceType("FUNDO");
        r.setTitle("Moodle CM test");
        r.setResourceType("MODULE");
        r.setExternalRef("501");
        r.setMoodleCmId(424242L);
        r.setAppCode("experience");
        r.setActive(true);
        resourceRepository.save(r);
    }

    @Test
    void ingest_writes_completion_progress_and_snapshot() throws Exception {
        String json =
                """
                {"statuses":[{"cmid":424242,"state":1,"modname":"page"}]}
                """;
        JsonNode moodle = mapper.readTree(json);
        var out =
                facade.ingestMoodleActivityCompletionResponse(
                        TENANT, "501", "7", moodle, "PROVIDER_PUBLIC_ID", "prov-moodle-1", "corr-1");

        assertThat(out.get("completionsWritten")).isEqualTo(1);
        assertThat(out.get("snapshotId")).isNotNull();
        assertThat(snapshotRepository.count()).isEqualTo(1);
        boolean exists =
                completionRepository.existsByTenantIdAndSourceTypeAndSourceRef(TENANT, "MOODLE_WS", "501:424242");
        assertThat(exists).isTrue();
    }
}
