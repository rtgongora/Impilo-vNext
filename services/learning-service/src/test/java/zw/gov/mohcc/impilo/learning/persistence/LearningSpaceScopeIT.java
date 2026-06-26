package zw.gov.mohcc.impilo.learning.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;

/** C0 — learning_space_id scopes content; null = national, set = academy-scoped. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearningSpaceScopeIT {

    private static final UUID TENANT = UUID.fromString("c0c0c0c0-0000-4000-8000-000000000001");

    @Autowired private CourseRepository courseRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    private CourseEntity course(String code, UUID space) {
        CourseEntity c = new CourseEntity();
        c.setTenantId(TENANT);
        c.setCode(code);
        c.setTitle(code);
        c.setStatus("PUBLISHED");
        c.setLanguage("en");
        c.setLearningSpaceId(space);
        return c;
    }

    @Test
    void courseScopedByLearningSpace() {
        UUID spaceA = UUID.randomUUID();
        courseRepository.save(course("NATIONAL-1", null));
        courseRepository.save(course("ACADEMY-A-1", spaceA));
        courseRepository.save(course("ACADEMY-A-2", spaceA));

        assertThat(courseRepository.findByTenantIdAndLearningSpaceId(TENANT, spaceA, PageRequest.of(0, 10))).hasSize(2);
        // national content (null space) is not returned by a space-scoped query
        assertThat(courseRepository.findByTenantIdAndLearningSpaceId(TENANT, UUID.randomUUID(), PageRequest.of(0, 10))).isEmpty();
    }
}
