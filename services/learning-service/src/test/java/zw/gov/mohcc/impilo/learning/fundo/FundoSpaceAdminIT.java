package zw.gov.mohcc.impilo.learning.fundo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.integration.MoodleWebServiceClient;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningProviderEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningSpaceEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningProviderRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningSpaceRepository;

/** C3 — binding content to a space + space-scoped views (delegated admin). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FundoSpaceAdminIT {

    private static final UUID TENANT = UUID.fromString("c3c3c3c3-0000-4000-8000-000000000001");

    @Autowired private FundoSpaceAdminService spaceAdmin;
    @Autowired private CourseRepository courseRepository;
    @Autowired private LearningProviderRepository providerRepository;
    @Autowired private LearningSpaceRepository spaceRepository;
    @MockBean private MoodleWebServiceClient moodleWebServiceClient;

    @SuppressWarnings("unchecked")
    @Test
    void bindCourseToSpaceAndScope() {
        LearningProviderEntity provider = new LearningProviderEntity();
        provider.setTenantId(TENANT);
        provider.setProviderKind("ORGANISATION");
        provider.setDisplayName("Academy ZW");
        providerRepository.save(provider);

        LearningSpaceEntity space = new LearningSpaceEntity();
        space.setTenantId(TENANT);
        space.setProviderId(provider.getId());
        space.setName("Nursing Academy");
        spaceRepository.save(space);

        CourseEntity course = new CourseEntity();
        course.setTenantId(TENANT);
        course.setCode("SP-101");
        course.setTitle("Space course");
        course.setStatus("PUBLISHED");
        course.setLanguage("en");
        courseRepository.save(course);

        // initially national (no space) -> not in space-scoped list
        assertThat(((List<?>) spaceAdmin.listSpaceCourses(TENANT, space.getId().toString(), 50).get("items"))).isEmpty();

        // bind to space
        Map<String, Object> bound = spaceAdmin.assignCourseToSpace(TENANT, course.getId().toString(), space.getId().toString());
        assertThat(bound.get("learningSpaceId")).isEqualTo(space.getId().toString());

        assertThat(((List<?>) spaceAdmin.listSpaceCourses(TENANT, space.getId().toString(), 50).get("items"))).hasSize(1);
        assertThat(spaceAdmin.spaceSummary(TENANT, space.getId().toString()).get("courseCount")).isEqualTo(1L);

        // return to national scope
        spaceAdmin.assignCourseToSpace(TENANT, course.getId().toString(), null);
        assertThat(((List<?>) spaceAdmin.listSpaceCourses(TENANT, space.getId().toString(), 50).get("items"))).isEmpty();
    }
}
