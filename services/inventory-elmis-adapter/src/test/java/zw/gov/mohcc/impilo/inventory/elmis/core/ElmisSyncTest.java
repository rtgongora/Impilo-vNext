package zw.gov.mohcc.impilo.inventory.elmis.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.inventory.elmis.config.ElmisAdapterProperties;
import zw.gov.mohcc.impilo.inventory.elmis.persistence.entity.SyncStateEntity;
import zw.gov.mohcc.impilo.inventory.elmis.persistence.repository.SyncStateRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G023: a sync must reach a terminal state. When no real eLMIS endpoint is
 * configured it fails closed (NOT_LIVE); against a configured endpoint it completes.
 */
@ExtendWith(MockitoExtension.class)
class ElmisSyncTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private RestTemplate elmisRestTemplate;
    @Mock private SyncStateRepository repository;

    private ElmisAdapterProperties propsWithUrl(String url) {
        ElmisAdapterProperties props = new ElmisAdapterProperties();
        props.getConnector().setExternalUrl(url);
        return props;
    }

    @Test
    void failsClosedWhenEndpointNotConfigured() {
        // Default placeholder URL = not configured
        ElmisSyncConnector connector = new ElmisSyncConnector(
                propsWithUrl(ElmisSyncConnector.DEFAULT_PLACEHOLDER_URL), elmisRestTemplate);

        SyncResult result = connector.sync(newEntity());

        assertThat(result.status()).isEqualTo(SyncResult.FAILED);
        assertThat(result.message()).contains("NOT_LIVE");
        verify(elmisRestTemplate, never()).postForEntity(any(), any(), any());
    }

    @Test
    void completesAgainstConfiguredEndpoint() throws Exception {
        ElmisSyncConnector connector = new ElmisSyncConnector(
                propsWithUrl("https://elmis.health.gov.zw/api"), elmisRestTemplate);
        JsonNode body = MAPPER.readTree("{\"recordsSynced\":42}");
        when(elmisRestTemplate.postForEntity(eq("https://elmis.health.gov.zw/api/sync"), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(body));

        SyncResult result = connector.sync(newEntity());

        assertThat(result.status()).isEqualTo(SyncResult.COMPLETED);
        assertThat(result.recordsSynced()).isEqualTo(42);
    }

    @Test
    void failsWhenEndpointErrors() {
        ElmisSyncConnector connector = new ElmisSyncConnector(
                propsWithUrl("https://elmis.health.gov.zw/api"), elmisRestTemplate);
        when(elmisRestTemplate.postForEntity(any(String.class), any(), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("connection refused"));

        SyncResult result = connector.sync(newEntity());

        assertThat(result.status()).isEqualTo(SyncResult.FAILED);
        assertThat(result.message()).contains("connection refused");
    }

    @Test
    void triggerSyncNeverLeavesInProgress() {
        ElmisSyncConnector connector = new ElmisSyncConnector(
                propsWithUrl(ElmisSyncConnector.DEFAULT_PLACEHOLDER_URL), elmisRestTemplate);
        SyncStateService service = new SyncStateService(repository, connector);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        SyncStateEntity out = service.triggerSync(UUID.randomUUID(), UUID.randomUUID(), "STOCK");

        assertThat(out.getStatus()).isNotEqualTo("IN_PROGRESS");
        assertThat(out.getStatus()).isEqualTo("FAILED");
        assertThat(out.getErrorMessage()).contains("NOT_LIVE");
    }

    private SyncStateEntity newEntity() {
        SyncStateEntity e = new SyncStateEntity();
        e.setTenantId(UUID.randomUUID());
        e.setFacilityId(UUID.randomUUID());
        e.setSyncType("STOCK");
        return e;
    }
}
