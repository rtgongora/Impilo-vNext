package zw.gov.mohcc.impilo.guidance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.guidance.persistence.entity.ServiceAdvisoryEntity;
import zw.gov.mohcc.impilo.guidance.persistence.repository.AdvisoryImpressionRepository;
import zw.gov.mohcc.impilo.guidance.persistence.repository.ServiceAdvisoryRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Governance + allow-listing tests for Notices & Bulletins (V017):
 * (1) recall/regulatory notices cannot be PUBLISHED without a named approver — the legal
 * guardrail; (2) the public notices view exposes only allow-listed fields (no authoring
 * internals), and unknown categories never error.
 */
@ExtendWith(MockitoExtension.class)
class AdvisoryNoticesGovernanceTest {

    @Mock private ServiceAdvisoryRepository repo;
    @Mock private AdvisoryImpressionRepository impressionRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    private AdvisoryAdminService adminService() {
        return new AdvisoryAdminService(repo, mapper);
    }

    private AdvisoryResolveService resolveService() {
        return new AdvisoryResolveService(repo, impressionRepo, mapper);
    }

    private static ServiceAdvisoryEntity notice(String category, String status, String approvedBy) {
        ServiceAdvisoryEntity a = new ServiceAdvisoryEntity();
        a.setCategory(category);
        a.setSeverity("SERVICE_NOTICE");
        a.setVariant("BANNER");
        a.setStatus(status);
        a.setTitle("Notice");
        a.setBody("Body");
        a.setApprovedBy(approvedBy);
        try {
            var f = ServiceAdvisoryEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(a, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return a;
    }

    @Test
    void regulatoryNoticeCannotBePublishedWithoutAnApprover() {
        UUID id = UUID.randomUUID();
        ServiceAdvisoryEntity a = notice("REGULATORY_NOTICE", "DRAFT", null);
        when(repo.findByIdAndTenantId(eq(id), any())).thenReturn(java.util.Optional.of(a));

        assertThatThrownBy(() -> adminService().publish("national-spine", id))
                .hasMessageContaining("must be approved");
    }

    @Test
    void safetyRecallCannotBePublishedWithoutAnApprover() {
        UUID id = UUID.randomUUID();
        ServiceAdvisoryEntity a = notice("SAFETY_RECALL", "DRAFT", null);
        when(repo.findByIdAndTenantId(eq(id), any())).thenReturn(java.util.Optional.of(a));

        assertThatThrownBy(() -> adminService().publish("national-spine", id))
                .hasMessageContaining("must be approved");
    }

    @Test
    void campaignNoticePublishesWithoutAnApprover() {
        UUID id = UUID.randomUUID();
        ServiceAdvisoryEntity a = notice("PUBLIC_HEALTH_CAMPAIGN", "DRAFT", null);
        when(repo.findByIdAndTenantId(eq(id), any())).thenReturn(java.util.Optional.of(a));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> view = adminService().publish("national-spine", id);
        assertThat(view.get("status")).isEqualTo("PUBLISHED");
    }

    @Test
    void publicNoticeViewIsAllowListedAndUnknownCategoryDoesNotError() {
        ServiceAdvisoryEntity a = notice("PUBLIC_HEALTH_CAMPAIGN", "PUBLISHED", "moh");
        a.setCreatedBy("secret-author");
        a.setStartsAt(OffsetDateTime.now().minusDays(1));
        when(repo.findActiveNotices(eq("national-spine"), any(), any())).thenReturn(List.of(a));

        // Unknown category is coerced to unfiltered (null) rather than throwing.
        Map<String, Object> result = resolveService().listNotices("NONSENSE", null, 0, 20);

        List<Map<String, Object>> notices = (List<Map<String, Object>>) result.get("notices");
        assertThat(notices).hasSize(1);
        Map<String, Object> view = notices.get(0);
        assertThat(view).containsKeys("category", "title", "body", "severity", "publishedAt");
        assertThat(view).doesNotContainKeys("createdBy", "approvedBy", "tenantId");
    }
}
