package zw.gov.mohcc.impilo.nhume.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryProofEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryType;
import zw.gov.mohcc.impilo.nhume.integration.writeback.NhumeIntegrationWriteBackService;
import zw.gov.mohcc.impilo.nhume.integration.writeback.NhumeWriteBackGateway;
import zw.gov.mohcc.impilo.nhume.integration.writeback.WriteBackOutcome;
import zw.gov.mohcc.impilo.nhume.repository.DeliveryProofRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NhumeIntegrationWriteBackServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static DeliveryProofRepository emptyProofRepo() {
        DeliveryProofRepository repo = mock(DeliveryProofRepository.class);
        when(repo.findByDeliveryIdOrderByCapturedAtAsc(any(UUID.class))).thenReturn(List.of());
        return repo;
    }

    private static final class RecordingGateway implements NhumeWriteBackGateway {
        final java.util.List<String> calls = new java.util.ArrayList<>();
        WriteBackOutcome next = WriteBackOutcome.ok("done");

        @Override
        public WriteBackOutcome orosReceiveByOrder(String ref, WriteBackContext ctx) {
            calls.add("OROS:" + ref + ":tenant=" + ctx.tenantId());
            return next;
        }

        @Override
        public WriteBackOutcome madiCompleteOrder(String ref, WriteBackContext ctx) {
            calls.add("MADI:" + ref);
            return next;
        }

        @Override
        public WriteBackOutcome pctAcceptReferral(String ref, WriteBackContext ctx) {
            calls.add("PCT:" + ref);
            return next;
        }

        @Override
        public WriteBackOutcome msikaFlowDispatchStatus(String orderRef, String status,
                                                        String deliveryId, String proofRef,
                                                        WriteBackContext ctx) {
            calls.add("MSIKA_FLOW:" + orderRef + ":" + status
                    + ":delivery=" + deliveryId + ":proof=" + proofRef);
            return next;
        }
    }

    private static DeliveryRequestEntity delivery(String metadataJson) {
        DeliveryRequestEntity d = new DeliveryRequestEntity(
                UUID.randomUUID(), UUID.randomUUID(), "NHM-TEST", "TEST");
        d.setPodId("national-spine");
        d.setMetadataJson(metadataJson);
        return d;
    }

    @Test
    void dispatchesOneWriteBackPerLinkedSystem() {
        RecordingGateway gateway = new RecordingGateway();
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), true);

        Map<String, WriteBackOutcome> outcomes = service.onDelivered(delivery("""
                {"cargoProfile":"SPECIMEN","links":{
                  "orosOrderRef":"ORD-1","madiOrderRef":"MO-2","pctReferralRef":"REF-3"}}"""), null);

        assertThat(outcomes.keySet()).containsExactly("OROS", "MADI", "PCT");
        assertThat(gateway.calls).hasSize(3);
        assertThat(outcomes.values()).allMatch(o -> o.status() == WriteBackOutcome.Status.OK);
    }

    @Test
    void duraLinkIsHonestlySkippedNotSilentlyDropped() {
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(new RecordingGateway(), MAPPER,
                        emptyProofRepo(), true);

        Map<String, WriteBackOutcome> outcomes = service.onDelivered(delivery(
                "{\"links\":{\"duraRequisitionRef\":\"REQ-9\"}}"), null);

        assertThat(outcomes).containsOnlyKeys("DURA");
        assertThat(outcomes.get("DURA").status())
                .isEqualTo(WriteBackOutcome.Status.SKIPPED_NO_TRANSITION);
    }

    @Test
    void noLinksOrDisabledMeansNoCalls() {
        RecordingGateway gateway = new RecordingGateway();
        NhumeIntegrationWriteBackService enabled =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), true);
        assertThat(enabled.onDelivered(delivery("{\"cargoProfile\":\"GENERAL\"}"), null)).isEmpty();
        assertThat(enabled.onDelivered(delivery(null), null)).isEmpty();
        assertThat(enabled.onDelivered(delivery("not-json"), null)).isEmpty();

        NhumeIntegrationWriteBackService disabled =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), false);
        assertThat(disabled.onDelivered(delivery(
                "{\"links\":{\"orosOrderRef\":\"ORD-1\"}}"), null)).isEmpty();
        assertThat(gateway.calls).isEmpty();
    }

    @Test
    void gatewayExceptionBecomesRecordedFailureNotThrow() {
        NhumeWriteBackGateway throwing = new NhumeWriteBackGateway() {
            @Override public WriteBackOutcome orosReceiveByOrder(String r, WriteBackContext c) {
                throw new IllegalStateException("boom");
            }
            @Override public WriteBackOutcome madiCompleteOrder(String r, WriteBackContext c) {
                return WriteBackOutcome.ok("fine");
            }
            @Override public WriteBackOutcome pctAcceptReferral(String r, WriteBackContext c) {
                return WriteBackOutcome.ok("fine");
            }
            @Override public WriteBackOutcome msikaFlowDispatchStatus(
                    String o, String s, String d, String p, WriteBackContext c) {
                throw new IllegalStateException("msika boom");
            }
        };
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(throwing, MAPPER, emptyProofRepo(), true);

        Map<String, WriteBackOutcome> outcomes = service.onDelivered(delivery("""
                {"links":{"orosOrderRef":"ORD-1","madiOrderRef":"MO-2","msikaFlowOrderRef":"MKT-3"}}"""), null);

        assertThat(outcomes.get("OROS").status()).isEqualTo(WriteBackOutcome.Status.FAILED);
        assertThat(outcomes.get("MADI").status()).isEqualTo(WriteBackOutcome.Status.OK);
        assertThat(outcomes.get("MSIKA_FLOW").status()).isEqualTo(WriteBackOutcome.Status.FAILED);
    }

    @Test
    void mergeOutcomesWritesLinksWritebackBlockAndPreservesMetadata() throws Exception {
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(new RecordingGateway(), MAPPER,
                        emptyProofRepo(), true);

        String merged = service.mergeOutcomes(
                "{\"cargoProfile\":\"BLOOD\",\"links\":{\"madiOrderRef\":\"MO-2\"}}",
                Map.of("MADI", WriteBackOutcome.failed("MADI rejected the transition: HTTP 409")));

        Map<?, ?> parsed = MAPPER.readValue(merged, Map.class);
        assertThat(parsed.get("cargoProfile")).isEqualTo("BLOOD");
        Map<?, ?> writeback = (Map<?, ?>) parsed.get("links_writeback");
        Map<?, ?> madi = (Map<?, ?>) writeback.get("MADI");
        assertThat(madi.get("status")).isEqualTo("FAILED");
        assertThat(madi.get("detail").toString()).contains("409");
        assertThat(madi.get("at")).isNotNull();
        assertThat(((Map<?, ?>) parsed.get("links")).get("madiOrderRef")).isEqualTo("MO-2");
    }

    @Test
    void writeBackCarriesDeliveryTrustContext() {
        RecordingGateway gateway = new RecordingGateway();
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), true);
        DeliveryRequestEntity d = delivery("{\"links\":{\"orosOrderRef\":\"ORD-7\"}}");

        service.onDelivered(d, null);

        assertThat(gateway.calls).containsExactly(
                List.of("OROS:ORD-7:tenant=" + d.getTenantId()).get(0));
    }

    // ─── Msika Flow marketplace dispatch write-back ─────────────────────────

    @Test
    void deliveredMarketplaceLinkReportsDeliveredToMsikaFlow() {
        RecordingGateway gateway = new RecordingGateway();
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), true);
        DeliveryRequestEntity d = delivery("{\"links\":{\"msikaFlowOrderRef\":\"MKT-ORD-9\"}}");

        Map<String, WriteBackOutcome> outcomes = service.onDelivered(d, null);

        assertThat(outcomes).containsOnlyKeys("MSIKA_FLOW");
        assertThat(outcomes.get("MSIKA_FLOW").status()).isEqualTo(WriteBackOutcome.Status.OK);
        assertThat(gateway.calls).containsExactly(
                "MSIKA_FLOW:MKT-ORD-9:DELIVERED:delivery=" + d.getDeliveryId() + ":proof=null");
    }

    @Test
    void deliveredMarketplaceColumnWithoutLinksStillReports() {
        RecordingGateway gateway = new RecordingGateway();
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), true);
        DeliveryRequestEntity d = delivery(null);
        d.setDeliveryType(DeliveryType.MARKETPLACE.name());
        d.setMarketplaceOrderRef("MKT-ORD-10");

        Map<String, WriteBackOutcome> outcomes = service.onDelivered(d, null);

        assertThat(outcomes).containsOnlyKeys("MSIKA_FLOW");
        assertThat(gateway.calls).containsExactly(
                "MSIKA_FLOW:MKT-ORD-10:DELIVERED:delivery=" + d.getDeliveryId() + ":proof=null");
    }

    @Test
    void deliveredMarketplaceCarriesLatestDeliveryProofRef() {
        RecordingGateway gateway = new RecordingGateway();
        DeliveryRequestEntity d = delivery("{\"links\":{\"msikaFlowOrderRef\":\"MKT-ORD-11\"}}");

        DeliveryProofEntity pickupProof = new DeliveryProofEntity();
        pickupProof.setProofId(UUID.randomUUID());
        pickupProof.setProofStage("PICKUP");
        DeliveryProofEntity deliveryProof = new DeliveryProofEntity();
        deliveryProof.setProofId(UUID.randomUUID());
        deliveryProof.setProofStage("DELIVERY");
        DeliveryProofRepository proofRepo = mock(DeliveryProofRepository.class);
        when(proofRepo.findByDeliveryIdOrderByCapturedAtAsc(d.getDeliveryId()))
                .thenReturn(List.of(pickupProof, deliveryProof));

        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, proofRepo, true);
        service.onDelivered(d, null);

        assertThat(gateway.calls).containsExactly(
                "MSIKA_FLOW:MKT-ORD-11:DELIVERED:delivery=" + d.getDeliveryId()
                        + ":proof=" + deliveryProof.getProofId());
    }

    @Test
    void nonMarketplaceDeliveryNeverCallsMsikaFlow() {
        RecordingGateway gateway = new RecordingGateway();
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), true);
        DeliveryRequestEntity d = delivery("{\"links\":{\"orosOrderRef\":\"ORD-1\"}}");

        service.onDelivered(d, null);
        service.onPickedUp(d, null);

        assertThat(gateway.calls).noneMatch(c -> c.startsWith("MSIKA_FLOW"));
    }

    @Test
    void pickedUpMarketplaceReportsPickedUpOnly() {
        RecordingGateway gateway = new RecordingGateway();
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), true);
        DeliveryRequestEntity d = delivery(
                "{\"links\":{\"msikaFlowOrderRef\":\"MKT-ORD-12\",\"orosOrderRef\":\"ORD-1\"}}");

        Map<String, WriteBackOutcome> outcomes = service.onPickedUp(d, null);

        // Pickup is a marketplace milestone only — OROS/MADI/PCT fire on arrival.
        assertThat(outcomes).containsOnlyKeys("MSIKA_FLOW");
        assertThat(gateway.calls).containsExactly(
                "MSIKA_FLOW:MKT-ORD-12:PICKED_UP:delivery=" + d.getDeliveryId() + ":proof=null");
    }

    @Test
    void pickedUpGatewayFailureIsRecordedNotThrown() {
        RecordingGateway gateway = new RecordingGateway();
        gateway.next = WriteBackOutcome.failed("MSIKA_FLOW unreachable: ConnectException");
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), true);
        DeliveryRequestEntity d = delivery("{\"links\":{\"msikaFlowOrderRef\":\"MKT-ORD-13\"}}");

        Map<String, WriteBackOutcome> outcomes = service.onPickedUp(d, null);

        assertThat(outcomes.get("MSIKA_FLOW").status()).isEqualTo(WriteBackOutcome.Status.FAILED);
    }

    @Test
    void pickedUpDisabledOrNoMarketplaceRefIsQuietNoop() {
        RecordingGateway gateway = new RecordingGateway();
        NhumeIntegrationWriteBackService disabled =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), false);
        assertThat(disabled.onPickedUp(
                delivery("{\"links\":{\"msikaFlowOrderRef\":\"MKT-ORD-14\"}}"), null)).isEmpty();

        NhumeIntegrationWriteBackService enabled =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, emptyProofRepo(), true);
        // MARKETPLACE type without any order ref: nothing to report (logged).
        DeliveryRequestEntity noRef = delivery(null);
        noRef.setDeliveryType(DeliveryType.MARKETPLACE.name());
        assertThat(enabled.onPickedUp(noRef, null)).isEmpty();
        assertThat(gateway.calls).isEmpty();
    }
}
