package zw.gov.mohcc.impilo.surgery.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalSpecialtyIndicationEntity;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalOperativeTemplateRepository;
import zw.gov.mohcc.impilo.surgery.persistence.repository.SurgicalSpecialtyIndicationRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** SB-2 — specialty content read API (§6). Proves the 15-specialty vocabulary is enforced. */
@ExtendWith(MockitoExtension.class)
class SpecialtyContentServiceTest {

    @Mock SurgicalSpecialtyIndicationRepository indicationRepository;
    @Mock SurgicalOperativeTemplateRepository templateRepository;

    private SpecialtyContentService service;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SpecialtyContentService(indicationRepository, templateRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void anInventedSpecialtyIsRefused() {
        SurgeryDomainException ex = assertThrows(SurgeryDomainException.class,
                () -> service.indicationsFor("ASTROLOGY"));
        assertEquals("INVALID_SPECIALTY", ex.getCode());
    }

    @Test
    void allFifteenSpecialtiesAreValid() {
        assertEquals(15, SpecialtyContentService.SPECIALTIES.size());
    }

    @Test
    void indicationsForAValidSpecialtyAreReturned() {
        SurgicalSpecialtyIndicationEntity e = new SurgicalSpecialtyIndicationEntity();
        // no setters exist beyond what JPA needs; rely on defaults for maturity/authority
        when(indicationRepository.findByTenantIdAndSpecialtyOrderByIndicationCodeAsc(tenant, "COLORECTAL"))
                .thenReturn(List.of(e));

        List<?> result = service.indicationsFor("COLORECTAL");

        assertEquals(1, result.size());
    }
}
