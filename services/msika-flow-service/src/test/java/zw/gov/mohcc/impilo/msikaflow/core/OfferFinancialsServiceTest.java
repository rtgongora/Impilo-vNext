package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.domain.FundingMode;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceProfile;
import zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus;
import zw.gov.mohcc.impilo.msikaflow.domain.PublicationMode;
import zw.gov.mohcc.impilo.msikaflow.integration.CostaClient;
import zw.gov.mohcc.impilo.msikaflow.integration.CoverageClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OF-B9/OF-B8 — the per-offer financial block: COSTA charge → Ruvimbo
 * liability ladder → PA posture; §10.4 degradation to UNVERIFIED; §10.5
 * estimate-never-final labelling; fail-closed funding-mode defaults.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferFinancialsServiceTest {

    @Mock private CostaClient costaClient;
    @Mock private CoverageClient coverageClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private OfferFinancialsService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID COVERAGE_ID = UUID.randomUUID();

    private MarketplaceRequestEntity request;
    private FulfillmentOfferEntity offer;
    private FulfillmentOfferLineEntity line;

    @BeforeEach
    void setUp() {
        service = new OfferFinancialsService(costaClient, coverageClient, mapper);

        request = new MarketplaceRequestEntity();
        request.setRequestId("01REQAAAAAAAAAAAAAAAAAAAAA");
        request.setTenantId(TENANT);
        request.setProfile(MarketplaceProfile.MEDICATION);
        request.setPublicationMode(PublicationMode.INVITED);

        offer = new FulfillmentOfferEntity();
        offer.setOfferId("01OFFAAAAAAAAAAAAAAAAAAAAA");
        offer.setStatus(OfferStatus.ACTIVE);
        offer.setPriceTotal(new BigDecimal("120.00"));
        offer.setCurrency("ZWG");

        line = new FulfillmentOfferLineEntity();
        line.setOfferLineId("01LINAAAAAAAAAAAAAAAAAAAAA");
        line.setOfferId(offer.getOfferId());
        line.setItemCode("ZIBO-X");
        line.setQuantity(2);
    }

    private CoverageClient.LiabilityEstimate estimate(UUID id, String benefitCode,
                                                      String charge, String payer, String patient,
                                                      boolean requiresPa) {
        return new CoverageClient.LiabilityEstimate(id, "CPID-1", benefitCode,
                new BigDecimal(charge), new BigDecimal(payer), new BigDecimal(patient),
                "USD", requiresPa, "assumptions");
    }

    // ── funding-mode defaults (fail-closed) ──────────────────────────────

    @Test
    void noFundingElection_defaultsToSelfPay_fullPriceIsLiability() {
        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.STATUS_SELF_PAY, block.status());
        assertEquals(FundingMode.SELF_PAY, block.fundingMode());
        assertEquals(new BigDecimal("120.00"), block.patientLiability());
        assertEquals(BigDecimal.ZERO, block.coveredAmount());
        assertTrue(block.paSatisfied());
        // engines never consulted for an explicit/default self-pay
        verifyNoInteractions(costaClient, coverageClient);
    }

    @Test
    void payerCovered_withoutCoverageId_isUnverified() {
        request.setFundingMode(FundingMode.PAYER_COVERED);
        request.setCoverageId(null);

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.STATUS_UNVERIFIED, block.status());
        assertFalse(block.payerVerified());
    }

    // ── the §10.4 ladder ─────────────────────────────────────────────────

    @Test
    void payerCovered_happyPath_sumsPerLineEstimates() {
        request.setFundingMode(FundingMode.PAYER_COVERED);
        request.setCoverageId(COVERAGE_ID);
        FulfillmentOfferLineEntity line2 = new FulfillmentOfferLineEntity();
        line2.setItemCode("ZIBO-Y");
        line2.setQuantity(1);
        when(costaClient.chargeForLines(anyList(), any())).thenReturn(Optional.of(
                new CostaClient.CostaCharge(new BigDecimal("100.00"),
                        Map.of("ZIBO-X", new BigDecimal("60.00"), "ZIBO-Y", new BigDecimal("40.00")))));
        UUID est1 = UUID.randomUUID();
        UUID est2 = UUID.randomUUID();
        when(coverageClient.estimateLiability(eq(COVERAGE_ID), eq("ZIBO-X"), eq(new BigDecimal("60.00")), any()))
                .thenReturn(new CoverageClient.EstimateResult(CoverageClient.Outcome.OK,
                        estimate(est1, "ZIBO-X", "60.00", "48.00", "12.00", false)));
        when(coverageClient.estimateLiability(eq(COVERAGE_ID), eq("ZIBO-Y"), eq(new BigDecimal("40.00")), any()))
                .thenReturn(new CoverageClient.EstimateResult(CoverageClient.Outcome.OK,
                        estimate(est2, "ZIBO-Y", "40.00", "20.00", "20.00", false)));

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line, line2), null);

        assertEquals(OfferFinancialsService.STATUS_VERIFIED, block.status());
        assertEquals(new BigDecimal("100.00"), block.grossCharge());
        assertEquals(new BigDecimal("68.00"), block.coveredAmount());
        assertEquals(new BigDecimal("32.00"), block.patientLiability());
        assertEquals(est1, block.calculationId());
        assertEquals(List.of(est1, est2), block.estimateIds());
        assertEquals("USD", block.currency());
        assertFalse(block.paRequired());
        assertTrue(block.payerVerified());
    }

    @Test
    void costaUnreachable_degradesToUnverified() {
        request.setFundingMode(FundingMode.PAYER_COVERED);
        request.setCoverageId(COVERAGE_ID);
        when(costaClient.chargeForLines(anyList(), any())).thenReturn(Optional.empty());

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.STATUS_UNVERIFIED, block.status());
        assertNull(block.patientLiability());
        verifyNoInteractions(coverageClient);
    }

    @Test
    void ruvimboUnreachable_degradesToUnverified() {
        request.setFundingMode(FundingMode.PAYER_COVERED);
        request.setCoverageId(COVERAGE_ID);
        when(costaClient.chargeForLines(anyList(), any())).thenReturn(Optional.of(
                new CostaClient.CostaCharge(new BigDecimal("60.00"),
                        Map.of("ZIBO-X", new BigDecimal("60.00")))));
        when(coverageClient.estimateLiability(any(), anyString(), any(), any()))
                .thenReturn(CoverageClient.EstimateResult.unreachable());

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.STATUS_UNVERIFIED, block.status());
    }

    @Test
    void ruvimboRefused_isNotCovered_notUnverified() {
        request.setFundingMode(FundingMode.PAYER_COVERED);
        request.setCoverageId(COVERAGE_ID);
        when(costaClient.chargeForLines(anyList(), any())).thenReturn(Optional.of(
                new CostaClient.CostaCharge(new BigDecimal("60.00"),
                        Map.of("ZIBO-X", new BigDecimal("60.00")))));
        when(coverageClient.estimateLiability(any(), anyString(), any(), any()))
                .thenReturn(CoverageClient.EstimateResult.refused());

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.STATUS_NOT_COVERED, block.status());
        assertFalse(block.payerVerified());
    }

    @Test
    void unpricedLine_makesWholeOfferUnverified_neverPartiallyTrusted() {
        request.setFundingMode(FundingMode.PAYER_COVERED);
        request.setCoverageId(COVERAGE_ID);
        // COSTA answered, but without this line's code
        when(costaClient.chargeForLines(anyList(), any())).thenReturn(Optional.of(
                new CostaClient.CostaCharge(new BigDecimal("60.00"),
                        Map.of("OTHER-CODE", new BigDecimal("60.00")))));

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.STATUS_UNVERIFIED, block.status());
        verifyNoInteractions(coverageClient);
    }

    // ── OF-B8 PA posture ─────────────────────────────────────────────────

    private void stubVerifiedPaLine() {
        when(costaClient.chargeForLines(anyList(), any())).thenReturn(Optional.of(
                new CostaClient.CostaCharge(new BigDecimal("60.00"),
                        Map.of("ZIBO-X", new BigDecimal("60.00")))));
        when(coverageClient.estimateLiability(eq(COVERAGE_ID), eq("ZIBO-X"), any(), any()))
                .thenReturn(new CoverageClient.EstimateResult(CoverageClient.Outcome.OK,
                        estimate(UUID.randomUUID(), "ZIBO-X", "60.00", "48.00", "12.00", true)));
        request.setFundingMode(FundingMode.PAYER_COVERED);
        request.setCoverageId(COVERAGE_ID);
    }

    @Test
    void paRequired_existingApprovedAuthorisation_satisfies() {
        stubVerifiedPaLine();
        UUID authId = UUID.randomUUID();
        when(coverageClient.findAuthorisations(eq("CPID-1"), any()))
                .thenReturn(Optional.of(List.of(
                        new CoverageClient.Authorisation(authId, "APPROVED", List.of("ZIBO-X")))));

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertTrue(block.paRequired());
        assertEquals(OfferFinancialsService.PA_APPROVED, block.paStatus());
        assertEquals(authId, block.paReference());
        assertTrue(block.paSatisfied());
        verify(coverageClient, never()).submitAuthorisation(any(), anyList(), any(), any());
    }

    @Test
    void paRequired_deniedOnly_isNotApproved() {
        stubVerifiedPaLine();
        when(coverageClient.findAuthorisations(eq("CPID-1"), any()))
                .thenReturn(Optional.of(List.of(
                        new CoverageClient.Authorisation(UUID.randomUUID(), "DENIED", List.of("ZIBO-X")))));

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.PA_NOT_APPROVED, block.paStatus());
        assertFalse(block.paSatisfied());
    }

    @Test
    void paRequired_noneExists_autoSubmitsMinimumNecessary_andIsPending() {
        stubVerifiedPaLine();
        UUID submittedId = UUID.randomUUID();
        when(coverageClient.findAuthorisations(eq("CPID-1"), any()))
                .thenReturn(Optional.of(List.of()));
        when(coverageClient.submitAuthorisation(eq(COVERAGE_ID), eq(List.of("ZIBO-X")), any(), any()))
                .thenReturn(Optional.of(submittedId));

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.PA_PENDING, block.paStatus());
        assertEquals(submittedId, block.paReference());
        assertFalse(block.paSatisfied());
        // §10.6 minimum-necessary: coded benefit lines only — the client seam
        // carries codes + amounts, never clinical narrative.
        verify(coverageClient).submitAuthorisation(eq(COVERAGE_ID), eq(List.of("ZIBO-X")), any(), any());
    }

    @Test
    void paRequired_machineUnreachable_isUnverified_failClosed() {
        stubVerifiedPaLine();
        when(coverageClient.findAuthorisations(eq("CPID-1"), any())).thenReturn(Optional.empty());

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.PA_UNVERIFIED, block.paStatus());
        assertFalse(block.paSatisfied());
    }

    // ── §10.5 labelling contract ─────────────────────────────────────────

    @Test
    void toJson_alwaysCarriesEstimateNeverFinal_andNeverLeaksCpid() throws Exception {
        stubVerifiedPaLine();
        when(coverageClient.findAuthorisations(eq("CPID-1"), any()))
                .thenReturn(Optional.of(List.of(
                        new CoverageClient.Authorisation(UUID.randomUUID(), "APPROVED", List.of("ZIBO-X")))));
        OfferFinancialsService.FinancialBlock verified =
                service.compute(request, offer, List.of(line), null);

        String verifiedJson = service.toJson(verified);
        String selfPayJson = service.toJson(service.compute(
                selfPayRequest(), offer, List.of(line), null));

        for (String json : List.of(verifiedJson, selfPayJson)) {
            var node = mapper.readTree(json);
            assertTrue(node.path("estimateNeverFinal").asBoolean(false),
                    "estimateNeverFinal must be carried on every block: " + json);
            assertFalse(json.contains("CPID-1"), "member CPID must never serialise: " + json);
        }
        var node = mapper.readTree(verifiedJson);
        assertEquals("VERIFIED", node.path("status").asText());
        assertEquals("12.00", node.path("patientLiability").asText());
        assertTrue(node.path("paRequired").asBoolean());
    }

    // ── OF-B8 medication mapping (the formulary half) ────────────────────

    private void makeMedicationRequest(String lineRef, String codingSystem) {
        request.setFundingMode(FundingMode.PAYER_COVERED);
        request.setCoverageId(COVERAGE_ID);
        request.setPublishedLinesJson("{\"profile\":\"MEDICATION\",\"lines\":[{\"lineRef\":\""
                + lineRef + "\",\"itemCode\":\"" + line.getItemCode()
                + "\",\"codingSystem\":\"" + codingSystem + "\",\"quantity\":2,"
                + "\"controlled\":false,\"coldChain\":false,\"substitutionAllowed\":true}]}");
        line.setRequestLineRef(lineRef);
    }

    @Test
    void atcCodedLine_passesAtcAsMedicationCode_andUsesMappedBenefitForPa() {
        line.setItemCode("N02AA01");
        makeMedicationRequest("L1", "http://www.whocc.no/atc");
        when(costaClient.chargeForLines(anyList(), any())).thenReturn(Optional.of(
                new CostaClient.CostaCharge(new BigDecimal("25.00"),
                        Map.of("N02AA01", new BigDecimal("25.00")))));
        // Formulary-shaped answer: coverage resolved the ATC code through
        // cv_formulary and returns the MAPPED plan benefit + the PA flag.
        when(coverageClient.estimateMedicationLiability(
                eq(COVERAGE_ID), eq("N02AA01"), eq("http://www.whocc.no/atc"),
                eq(new BigDecimal("25.00")), any()))
                .thenReturn(new CoverageClient.EstimateResult(CoverageClient.Outcome.OK,
                        estimate(UUID.randomUUID(), "ACUTE_MEDS", "25.00", "20.00", "5.00", true)));
        UUID submittedId = UUID.randomUUID();
        when(coverageClient.findAuthorisations(eq("CPID-1"), any()))
                .thenReturn(Optional.of(List.of()));
        when(coverageClient.submitAuthorisation(eq(COVERAGE_ID), eq(List.of("ACUTE_MEDS")), any(), any()))
                .thenReturn(Optional.of(submittedId));

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.STATUS_VERIFIED, block.status());
        assertEquals(new BigDecimal("20.00"), block.coveredAmount());
        assertEquals(new BigDecimal("5.00"), block.patientLiability());
        assertTrue(block.paRequired());
        assertEquals(OfferFinancialsService.PA_PENDING, block.paStatus());
        // The PA submission carries the MAPPED benefit code, not the raw ATC code.
        verify(coverageClient).submitAuthorisation(eq(COVERAGE_ID), eq(List.of("ACUTE_MEDS")), any(), any());
        // The interim (itemCode-as-benefit-code) path is never used for a medication line.
        verify(coverageClient, never()).estimateLiability(any(), anyString(), any(), any());
    }

    @Test
    void atcCodedLine_notOnFormulary_isHonestlyNonCovered_patientPaysFull() {
        line.setItemCode("Z88XX01");
        makeMedicationRequest("L1", "http://www.whocc.no/atc");
        when(costaClient.chargeForLines(anyList(), any())).thenReturn(Optional.of(
                new CostaClient.CostaCharge(new BigDecimal("75.00"),
                        Map.of("Z88XX01", new BigDecimal("75.00")))));
        // Formulary-shaped NOT_LISTED answer: estimate exists, payer share 0.
        when(coverageClient.estimateMedicationLiability(
                eq(COVERAGE_ID), eq("Z88XX01"), anyString(), any(), any()))
                .thenReturn(new CoverageClient.EstimateResult(CoverageClient.Outcome.OK,
                        estimate(UUID.randomUUID(), "Z88XX01", "75.00", "0.00", "75.00", false)));

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.STATUS_VERIFIED, block.status());
        assertEquals(new BigDecimal("0.00"), block.coveredAmount());
        assertEquals(new BigDecimal("75.00"), block.patientLiability());
        assertFalse(block.paRequired());
    }

    @Test
    void nonAtcCodedLine_staysOnBenefitCodePath() {
        makeMedicationRequest("L1", "https://impilo.gov.zw/fhir/CodeSystem/surgical-procedures");
        when(costaClient.chargeForLines(anyList(), any())).thenReturn(Optional.of(
                costaCharge60()));
        when(coverageClient.estimateLiability(eq(COVERAGE_ID), eq("ZIBO-X"), eq(new BigDecimal("60.00")), any()))
                .thenReturn(new CoverageClient.EstimateResult(CoverageClient.Outcome.OK,
                        estimate(UUID.randomUUID(), "ZIBO-X", "60.00", "48.00", "12.00", false)));

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.STATUS_VERIFIED, block.status());
        verify(coverageClient, never()).estimateMedicationLiability(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void missingSnapshot_treatsLinesAsNonMedication_neverGuesses() {
        request.setFundingMode(FundingMode.PAYER_COVERED);
        request.setCoverageId(COVERAGE_ID);
        request.setPublishedLinesJson(null);
        line.setRequestLineRef("L1");
        when(costaClient.chargeForLines(anyList(), any())).thenReturn(Optional.of(
                costaCharge60()));
        when(coverageClient.estimateLiability(eq(COVERAGE_ID), eq("ZIBO-X"), any(), any()))
                .thenReturn(new CoverageClient.EstimateResult(CoverageClient.Outcome.OK,
                        estimate(UUID.randomUUID(), "ZIBO-X", "60.00", "48.00", "12.00", false)));

        OfferFinancialsService.FinancialBlock block =
                service.compute(request, offer, List.of(line), null);

        assertEquals(OfferFinancialsService.STATUS_VERIFIED, block.status());
        verify(coverageClient, never()).estimateMedicationLiability(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void atcSystemDetection_normalisesSchemeAndTrailingSlash() {
        assertTrue(OfferFinancialsService.isAtcSystem("http://www.whocc.no/atc"));
        assertTrue(OfferFinancialsService.isAtcSystem("https://www.whocc.no/atc"));
        assertTrue(OfferFinancialsService.isAtcSystem("http://www.whocc.no/atc/"));
        assertTrue(OfferFinancialsService.isAtcSystem(" HTTP://WWW.WHOCC.NO/ATC "));
        assertFalse(OfferFinancialsService.isAtcSystem(null));
        assertFalse(OfferFinancialsService.isAtcSystem(""));
        assertFalse(OfferFinancialsService.isAtcSystem("https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty"));
    }

    /** COSTA charge fixture for the default ZIBO-X line. */
    private static CostaClient.CostaCharge costaCharge60() {
        return new CostaClient.CostaCharge(new BigDecimal("60.00"),
                Map.of("ZIBO-X", new BigDecimal("60.00")));
    }

    private MarketplaceRequestEntity selfPayRequest() {
        MarketplaceRequestEntity r = new MarketplaceRequestEntity();
        r.setRequestId("01REQBBBBBBBBBBBBBBBBBBBBB");
        r.setTenantId(TENANT);
        r.setProfile(MarketplaceProfile.MEDICATION);
        r.setPublicationMode(PublicationMode.INVITED);
        r.setFundingMode(FundingMode.SELF_PAY);
        return r;
    }
}
