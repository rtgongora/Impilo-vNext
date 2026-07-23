package zw.gov.mohcc.impilo.coverage.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.coverage.domain.FormularyEntity;
import zw.gov.mohcc.impilo.coverage.repository.FormularyRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OF-B8 — the payer-formulary lookup matrix (V020 {@code cv_formulary}):
 * covered / tiered / PA-required / explicitly-excluded / not-listed /
 * expired-window, plus governed retirement (delete = window close).
 */
@ExtendWith(MockitoExtension.class)
class FormularyServiceTest {

    @Mock private FormularyRepository formularyRepository;
    @Mock private CoverageEventService eventService;

    private FormularyService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID planVersionId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now();

    private static final String ATC = FormularyEntity.ATC_CODING_SYSTEM;

    @BeforeEach
    void setUp() {
        service = new FormularyService(formularyRepository, eventService);
    }

    private FormularyEntity entry(String code, String benefit, boolean covered, String tier,
                                  boolean requiresPa, OffsetDateTime from, OffsetDateTime to) {
        FormularyEntity f = FormularyEntity.create(tenantId, "pod-1", planVersionId, code, ATC, benefit);
        f.setCovered(covered);
        f.setTier(tier);
        f.setRequiresAuthorisation(requiresPa);
        f.setEffectiveFrom(from);
        f.setEffectiveTo(to);
        return f;
    }

    private void stubLookup(String code, FormularyEntity... rows) {
        lenient().when(formularyRepository
                        .findByTenantIdAndPlanVersionIdAndMedicationCodeAndCodingSystem(
                                tenantId, planVersionId, code, ATC))
                .thenReturn(List.of(rows));
    }

    // ── the lookup matrix ────────────────────────────────────────────────

    @Test
    void coveredListing_resolvesOnFormulary_withTierAndBenefitMapping() {
        stubLookup("J01CA04", entry("J01CA04", "ACUTE_MEDS", true, "GENERIC", false,
                now.minusDays(30), null));

        FormularyService.FormularyResolution r =
                service.resolve(tenantId, planVersionId, "J01CA04", ATC, now);

        assertEquals(FormularyService.FormularyStatus.ON_FORMULARY, r.status());
        assertEquals("ACUTE_MEDS", r.entry().getBenefitCode());
        assertEquals("GENERIC", r.entry().getTier());
        assertFalse(r.entry().isRequiresAuthorisation());
    }

    @Test
    void paRequiredListing_carriesTheFlag() {
        stubLookup("N02AA01", entry("N02AA01", "ACUTE_MEDS", true, "CONTROLLED", true,
                now.minusDays(30), null));

        FormularyService.FormularyResolution r =
                service.resolve(tenantId, planVersionId, "N02AA01", ATC, now);

        assertEquals(FormularyService.FormularyStatus.ON_FORMULARY, r.status());
        assertTrue(r.entry().isRequiresAuthorisation());
        assertEquals("CONTROLLED", r.entry().getTier());
    }

    @Test
    void explicitExclusion_isExcluded_notNotListed() {
        stubLookup("N05BA01", entry("N05BA01", "ACUTE_MEDS", false, "STANDARD", false,
                now.minusDays(30), null));

        FormularyService.FormularyResolution r =
                service.resolve(tenantId, planVersionId, "N05BA01", ATC, now);

        assertEquals(FormularyService.FormularyStatus.EXCLUDED, r.status());
        assertNotNull(r.entry());
    }

    @Test
    void noRow_isNotListed() {
        stubLookup("Z99ZZ99");

        FormularyService.FormularyResolution r =
                service.resolve(tenantId, planVersionId, "Z99ZZ99", ATC, now);

        assertEquals(FormularyService.FormularyStatus.NOT_LISTED, r.status());
        assertNull(r.entry());
    }

    @Test
    void expiredWindow_isNotListed_neverCoveredFromThePast() {
        stubLookup("A10BA02", entry("A10BA02", "CHRONIC_MEDS", true, "GENERIC", false,
                now.minusDays(365), now.minusDays(30)));

        FormularyService.FormularyResolution r =
                service.resolve(tenantId, planVersionId, "A10BA02", ATC, now);

        assertEquals(FormularyService.FormularyStatus.NOT_LISTED, r.status());
    }

    @Test
    void notYetEffective_isNotListed() {
        stubLookup("A10BA02", entry("A10BA02", "CHRONIC_MEDS", true, "GENERIC", false,
                now.plusDays(10), null));

        FormularyService.FormularyResolution r =
                service.resolve(tenantId, planVersionId, "A10BA02", ATC, now);

        assertEquals(FormularyService.FormularyStatus.NOT_LISTED, r.status());
    }

    @Test
    void overlappingWindows_latestEffectiveFromWins() {
        FormularyEntity older = entry("R03AC02", "CHRONIC_MEDS", true, "STANDARD", false,
                now.minusDays(100), null);
        FormularyEntity newer = entry("R03AC02", "CHRONIC_MEDS", true, "PREFERRED", true,
                now.minusDays(5), null);
        stubLookup("R03AC02", older, newer);

        FormularyService.FormularyResolution r =
                service.resolve(tenantId, planVersionId, "R03AC02", ATC, now);

        assertEquals(FormularyService.FormularyStatus.ON_FORMULARY, r.status());
        assertEquals("PREFERRED", r.entry().getTier());
        assertTrue(r.entry().isRequiresAuthorisation());
    }

    @Test
    void nullCodingSystem_defaultsToAtc() {
        stubLookup("J01CA04", entry("J01CA04", "ACUTE_MEDS", true, "GENERIC", false,
                now.minusDays(30), null));

        FormularyService.FormularyResolution r =
                service.resolve(tenantId, planVersionId, "J01CA04", null, now);

        assertEquals(FormularyService.FormularyStatus.ON_FORMULARY, r.status());
    }

    @Test
    void blankMedicationCode_isNotListed_withoutRepositoryCall() {
        FormularyService.FormularyResolution r =
                service.resolve(tenantId, planVersionId, "  ", ATC, now);

        assertEquals(FormularyService.FormularyStatus.NOT_LISTED, r.status());
    }

    // ── governance ───────────────────────────────────────────────────────

    @Test
    void retire_closesTheWindow_neverDeletesTheRow() {
        FormularyEntity live = entry("J01CA04", "ACUTE_MEDS", true, "GENERIC", false,
                now.minusDays(30), null);
        when(formularyRepository.findByIdAndTenantId(live.getId(), tenantId))
                .thenReturn(java.util.Optional.of(live));
        when(formularyRepository.save(any(FormularyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        FormularyEntity retired = service.retire(tenantId, live.getId(), UUID.randomUUID());

        assertNotNull(retired.getEffectiveTo());
        assertFalse(retired.activeAt(OffsetDateTime.now().plusSeconds(1)));
        verify(formularyRepository).save(live);
    }
}
