package zw.gov.mohcc.impilo.nhume.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryRequestEntity;
import zw.gov.mohcc.impilo.nhume.integration.writeback.NhumeIntegrationWriteBackService;
import zw.gov.mohcc.impilo.nhume.integration.writeback.NhumeWriteBackGateway;
import zw.gov.mohcc.impilo.nhume.integration.writeback.WriteBackOutcome;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NhumeIntegrationWriteBackServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
                new NhumeIntegrationWriteBackService(gateway, MAPPER, true);

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
                new NhumeIntegrationWriteBackService(new RecordingGateway(), MAPPER, true);

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
                new NhumeIntegrationWriteBackService(gateway, MAPPER, true);
        assertThat(enabled.onDelivered(delivery("{\"cargoProfile\":\"GENERAL\"}"), null)).isEmpty();
        assertThat(enabled.onDelivered(delivery(null), null)).isEmpty();
        assertThat(enabled.onDelivered(delivery("not-json"), null)).isEmpty();

        NhumeIntegrationWriteBackService disabled =
                new NhumeIntegrationWriteBackService(gateway, MAPPER, false);
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
        };
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(throwing, MAPPER, true);

        Map<String, WriteBackOutcome> outcomes = service.onDelivered(delivery("""
                {"links":{"orosOrderRef":"ORD-1","madiOrderRef":"MO-2"}}"""), null);

        assertThat(outcomes.get("OROS").status()).isEqualTo(WriteBackOutcome.Status.FAILED);
        assertThat(outcomes.get("MADI").status()).isEqualTo(WriteBackOutcome.Status.OK);
    }

    @Test
    void mergeOutcomesWritesLinksWritebackBlockAndPreservesMetadata() throws Exception {
        NhumeIntegrationWriteBackService service =
                new NhumeIntegrationWriteBackService(new RecordingGateway(), MAPPER, true);

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
                new NhumeIntegrationWriteBackService(gateway, MAPPER, true);
        DeliveryRequestEntity d = delivery("{\"links\":{\"orosOrderRef\":\"ORD-7\"}}");

        service.onDelivered(d, null);

        assertThat(gateway.calls).containsExactly(
                List.of("OROS:ORD-7:tenant=" + d.getTenantId()).get(0));
    }
}
