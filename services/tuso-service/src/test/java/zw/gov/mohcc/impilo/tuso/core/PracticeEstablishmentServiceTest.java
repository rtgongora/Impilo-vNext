package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PracticeEstablishmentCaseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.PracticeEstablishmentCaseRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeEstablishmentServiceTest {

    @Mock PracticeEstablishmentCaseRepository repository;

    private PracticeEstablishmentService service() {
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        return new PracticeEstablishmentService(repository);
    }

    private final UUID tenant = UUID.randomUUID();
    private final UUID applicant = UUID.randomUUID();

    @Test
    void draftHasNoFacility() {
        var c = service().createDraft(tenant, applicant, "Sunrise Pharmacy", "PHARMACY", "HARARE");
        assertThat(c.getStatus()).isEqualTo("DRAFT");
        assertThat(c.getCreatedFacilityUuid()).isNull(); // no facility before approval
    }

    @Test
    void facilityMaterialisesOnlyOnApproval() {
        var svc = service();
        PracticeEstablishmentCaseEntity draft = new PracticeEstablishmentCaseEntity();
        draft.setTenantId(tenant);
        draft.setStatus("SUBMITTED");
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(draft));

        var approved = svc.approve(tenant, id, "registrar-1", "meets requirements");
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getCreatedFacilityUuid()).isNotNull(); // facility exists now, and only now
    }
}
